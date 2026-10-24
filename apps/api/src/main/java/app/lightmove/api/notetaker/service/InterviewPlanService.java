package app.lightmove.api.notetaker.service;

import app.lightmove.api.billing.usage.constant.UsageKind;
import app.lightmove.api.billing.usage.model.MeteredUse;
import app.lightmove.api.billing.usage.service.FairUseGuard;
import app.lightmove.api.billing.usage.service.UsageRecorder;
import app.lightmove.api.candidate.dto.CandidateScorecardResponse;
import app.lightmove.api.candidate.model.CallHeldFields;
import app.lightmove.api.candidate.model.OutreachRecipient;
import app.lightmove.api.candidate.service.CandidateCallUpdates;
import app.lightmove.api.candidate.service.CandidateOutreachService;
import app.lightmove.api.candidate.service.CandidateScorecardService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.ratelimit.service.LlmBudget;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.enrichment.candidate.model.ExecutiveReadingText;
import app.lightmove.api.enrichment.candidate.service.ExecutiveReading;
import app.lightmove.api.notetaker.constant.InterviewQuestionSection;
import app.lightmove.api.notetaker.constant.InterviewQuestionSource;
import app.lightmove.api.notetaker.constant.RecordGap;
import app.lightmove.api.notetaker.dto.InterviewPlanResponse;
import app.lightmove.api.notetaker.dto.InterviewQuestionDto;
import app.lightmove.api.notetaker.dto.PlanCompetencyResponse;
import app.lightmove.api.notetaker.dto.SaveInterviewPlanRequest;
import app.lightmove.api.notetaker.model.InterviewPlan;
import app.lightmove.api.notetaker.model.InterviewQuestion;
import app.lightmove.api.notetaker.repository.InterviewPlanRepository;
import app.lightmove.api.outreach.model.KeptMeeting;
import app.lightmove.api.outreach.service.KeptMeetings;
import app.lightmove.api.position.model.AssessmentBrief;
import app.lightmove.api.position.model.BriefCompetency;
import app.lightmove.api.position.service.PositionService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Prepare questions for a call with an executive: one press drafts an interview plan against the brief and the gaps in
 * the record, and the consultant edits, ticks and saves it. The model call holds no transaction; a claim on the plan
 * keeps two presses to one call, and nothing is stored or charged unless a draft was stored. Pressing again redrafts
 * the model's questions and keeps every question the consultant wrote.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewPlanService {

    static final Duration CLAIM_LIFETIME = Duration.ofMinutes(3);
    /** A competency scored this high with evidence written is shown, and needs confirming rather than proving. */
    private static final int EVIDENCED_SCORE = 6;

    private final InterviewPlanRepository plans;
    private final CandidateOutreachService people;
    private final KeptMeetings meetings;
    private final CandidateCallUpdates updates;
    private final CandidateScorecardService scorecards;
    private final PositionService positions;
    private final ExecutiveReading executives;
    private final InterviewPlanner planner;
    private final LlmBudgetGuard llmBudget;
    private final FairUseGuard fairUse;
    private final UsageRecorder usage;
    private final UserRepository users;
    private final AuditService audit;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Transactional(readOnly = true)
    public InterviewPlanResponse read(UUID workspaceId, UUID projectId, UUID candidateId, UUID meetingId) {
        OutreachRecipient recipient = requireRecipient(workspaceId, projectId, candidateId);
        KeptMeeting meeting = requireMeeting(workspaceId, recipient, meetingId);
        return responseOf(meeting, findPlan(workspaceId, projectId, recipient, meeting).orElse(null),
                competenciesOf(workspaceId, projectId, recipient), missingOf(held(workspaceId, projectId, recipient)));
    }

    public InterviewPlanResponse draft(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId, UUID meetingId,
                                       HttpServletRequest httpRequest) {
        OutreachRecipient recipient = requireRecipient(workspaceId, projectId, candidateId);
        KeptMeeting meeting = transactions.execute(status -> requireMeeting(workspaceId, recipient, meetingId));
        llmBudget.require(LlmBudget.INTERVIEW_PLAN, userId);
        fairUse.check(workspaceId, userId, UsageKind.AI_ENRICH, 1);
        UUID planId = planFor(workspaceId, projectId, recipient, meeting);
        Instant now = clock.instant();
        Integer claimed = transactions.execute(status -> plans.claimDrafting(planId, now, now.minus(CLAIM_LIFETIME)));
        if (claimed == null || claimed == 0) {
            throw ApiException.of(ErrorCode.INTERVIEW_PLAN_DRAFTING);
        }
        try {
            AssessmentBrief brief = positions.assessmentBriefOf(workspaceId, projectId);
            Set<String> evidenced = evidencedOf(workspaceId, projectId, recipient);
            Set<RecordGap> missing = EnumSet.noneOf(RecordGap.class);
            missing.addAll(missingOf(held(workspaceId, projectId, recipient)));
            ExecutiveReadingText executive = executives.of(workspaceId, projectId, recipient.candidateId());
            List<InterviewQuestion> drafted;
            try {
                drafted = planner.draft(brief, executive, evidenced, missing);
            } catch (ApiException refused) {
                throw refused;
            } catch (RuntimeException failed) {
                log.warn("Interview plan for {} not drafted: {}", planId, failed.toString());
                throw ApiException.of(ErrorCode.AI_UNAVAILABLE);
            }
            Instant at = clock.instant();
            transactions.executeWithoutResult(status -> {
                InterviewPlan plan = plans.findById(planId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
                List<InterviewQuestion> next = new ArrayList<>(plan.getQuestions().stream()
                        .filter(question -> question.source() == InterviewQuestionSource.MANUAL)
                        .toList());
                next.addAll(0, drafted);
                plan.drafted(next, userId, at);
                audit.projectEvent(ProjectEventType.INTERVIEW_PLAN_DRAFTED, userId, workspaceId, projectId,
                                httpRequest)
                        .detail("candidateId", recipient.candidateId().toString())
                        .detail("planId", planId.toString())
                        .detail("questions", String.valueOf(drafted.size()))
                        .detail("notesSent", String.valueOf(executive.noteCount()))
                        .detail("documentsSent", String.valueOf(executive.documentCount()))
                        .record();
            });
            usage.record(new MeteredUse(workspaceId, userId, projectId, UsageKind.AI_ENRICH, 1,
                    "interview-plan:" + planId + ":" + at.toEpochMilli()));
        } finally {
            transactions.executeWithoutResult(status -> plans.releaseDrafting(planId));
        }
        return read(workspaceId, projectId, candidateId, meetingId);
    }

    @Transactional
    public InterviewPlanResponse save(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId, UUID meetingId,
                                      SaveInterviewPlanRequest request, HttpServletRequest httpRequest) {
        OutreachRecipient recipient = requireRecipient(workspaceId, projectId, candidateId);
        KeptMeeting meeting = requireMeeting(workspaceId, recipient, meetingId);
        InterviewPlan plan = findPlan(workspaceId, projectId, recipient, meeting)
                .orElseGet(() -> plans.saveAndFlush(InterviewPlan.forCall(workspaceId, projectId,
                        recipient.personId(), recipient.candidateId(), meeting.id(), meeting.title(),
                        meeting.startsAt())));
        Map<String, InterviewQuestion> held = plan.getQuestions().stream()
                .collect(Collectors.toMap(InterviewQuestion::id, Function.identity(), (first, second) -> first));
        List<InterviewQuestion> saved = request.questions().stream()
                .map(question -> savedOf(question, held))
                .toList();
        plan.saved(saved, userId, clock.instant());
        audit.projectEvent(ProjectEventType.INTERVIEW_PLAN_SAVED, userId, workspaceId, projectId, httpRequest)
                .detail("candidateId", recipient.candidateId().toString())
                .detail("planId", plan.getId().toString())
                .detail("questions", String.valueOf(saved.size()))
                .record();
        return responseOf(meeting, plan, competenciesOf(workspaceId, projectId, recipient),
                missingOf(held(workspaceId, projectId, recipient)));
    }

    /** The saved plan of the call a recording was sent for, for the live copilot; null when none was made. */
    @Transactional(readOnly = true)
    public Optional<InterviewPlan> planOfCall(UUID workspaceId, UUID projectId, UUID personId, Instant startsAt,
                                              String title) {
        return plans.findByWorkspaceIdAndProjectIdAndPersonIdAndStartsAtAndTitleKey(workspaceId, projectId, personId,
                startsAt, InterviewPlan.titleKeyOf(title));
    }

    /**
     * Whose a question is stays the server's: one read as the model's stays the model's however it was edited, and
     * anything else is the consultant's. A gap or competency is kept only on a question of its own section.
     */
    static InterviewQuestion savedOf(InterviewQuestionDto question, Map<String, InterviewQuestion> held) {
        InterviewQuestion before = question.id() == null ? null : held.get(question.id());
        InterviewQuestionSource source = before == null ? InterviewQuestionSource.MANUAL : before.source();
        boolean fit = question.section() == InterviewQuestionSection.FIT;
        return new InterviewQuestion(before == null ? UUID.randomUUID().toString() : before.id(), question.section(),
                fit ? question.panel() : null, fit ? InterviewPlanner.clip(question.competency(), 200) : null,
                question.section() == InterviewQuestionSection.GAP ? question.gap() : null,
                InterviewPlanner.clip(question.text(), InterviewPlanner.LONGEST_QUESTION),
                InterviewPlanner.clip(question.listenFor(), InterviewPlanner.LONGEST_LINE),
                InterviewPlanner.clip(question.why(), InterviewPlanner.LONGEST_LINE), question.included(), source);
    }

    /** Only what is empty: a value on file, whatever it says, is never sent and never asked about. */
    static List<RecordGap> missingOf(CallHeldFields held) {
        List<RecordGap> missing = new ArrayList<>();
        if (isBlank(held.noticePeriod())) {
            missing.add(RecordGap.NOTICE);
        }
        if (held.baseSalary() == null) {
            missing.add(RecordGap.PACKAGE);
        }
        if (isBlank(held.city()) && isBlank(held.country())) {
            missing.add(RecordGap.LOCATION);
        }
        if (held.relocationTagId() != null && !held.openToRelocate()) {
            missing.add(RecordGap.RELOCATION);
        }
        if (isBlank(held.motivation())) {
            // Expected package is written below the motivation; with that section empty neither was ever recorded.
            missing.add(RecordGap.MOTIVATION);
            missing.add(RecordGap.EXPECTED_PACKAGE);
        }
        return List.copyOf(missing);
    }

    private UUID planFor(UUID workspaceId, UUID projectId, OutreachRecipient recipient, KeptMeeting meeting) {
        try {
            return transactions.execute(status -> findPlan(workspaceId, projectId, recipient, meeting)
                    .orElseGet(() -> plans.saveAndFlush(InterviewPlan.forCall(workspaceId, projectId,
                            recipient.personId(), recipient.candidateId(), meeting.id(), meeting.title(),
                            meeting.startsAt())))
                    .getId());
        } catch (DataIntegrityViolationException raced) {
            return transactions.execute(status -> findPlan(workspaceId, projectId, recipient, meeting)
                    .orElseThrow(() -> ApiException.of(ErrorCode.INTERVIEW_PLAN_DRAFTING)).getId());
        }
    }

    private Optional<InterviewPlan> findPlan(UUID workspaceId, UUID projectId, OutreachRecipient recipient,
                                             KeptMeeting meeting) {
        return plans.findByWorkspaceIdAndProjectIdAndPersonIdAndStartsAtAndTitleKey(workspaceId, projectId,
                recipient.personId(), meeting.startsAt(), InterviewPlan.titleKeyOf(meeting.title()));
    }

    private List<PlanCompetencyResponse> competenciesOf(UUID workspaceId, UUID projectId,
                                                        OutreachRecipient recipient) {
        Set<String> evidenced = evidencedOf(workspaceId, projectId, recipient);
        List<BriefCompetency> competencies = positions.competenciesOf(workspaceId, projectId).competencies();
        return competencies.stream()
                .map(competency -> new PlanCompetencyResponse(competency.panel(), competency.name(),
                        competency.weight(), evidenced.contains(InterviewPlanner.keyOf(competency.name()))))
                .toList();
    }

    private Set<String> evidencedOf(UUID workspaceId, UUID projectId, OutreachRecipient recipient) {
        CandidateScorecardResponse scorecard = scorecards.read(workspaceId, projectId, recipient.candidateId());
        return scorecard.panels().stream()
                .flatMap(panel -> panel.criteria().stream())
                .filter(criterion -> criterion.score() != null && criterion.score() >= EVIDENCED_SCORE
                        && !isBlank(criterion.evidence()))
                .map(criterion -> InterviewPlanner.keyOf(criterion.name()))
                .collect(Collectors.toUnmodifiableSet());
    }

    private CallHeldFields held(UUID workspaceId, UUID projectId, OutreachRecipient recipient) {
        return updates.heldOf(workspaceId, projectId, recipient.candidateId());
    }

    private InterviewPlanResponse responseOf(KeptMeeting meeting, InterviewPlan plan,
                                             List<PlanCompetencyResponse> competencies, List<RecordGap> missing) {
        String savedBy = plan == null || plan.getSavedBy() == null ? null
                : users.findById(plan.getSavedBy()).map(User::getFullName).orElse(null);
        return new InterviewPlanResponse(meeting.id(), meeting.title(), meeting.startsAt(),
                plan != null && plan.isDrafted(), plan == null ? null : plan.getSavedAt(), savedBy, competencies,
                missing, plan == null ? List.of() : plan.getQuestions().stream()
                        .map(InterviewPlanService::dtoOf)
                        .toList());
    }

    static InterviewQuestionDto dtoOf(InterviewQuestion question) {
        return new InterviewQuestionDto(question.id(), question.section(), question.panel(), question.competency(),
                question.gap(), question.text(), question.listenFor(), question.why(), question.included(),
                question.source());
    }

    private KeptMeeting requireMeeting(UUID workspaceId, OutreachRecipient recipient, UUID meetingId) {
        return meetings.of(workspaceId, recipient.personId(), meetingId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private OutreachRecipient requireRecipient(UUID workspaceId, UUID projectId, UUID candidateId) {
        return people.currentRecipient(workspaceId, projectId, candidateId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
