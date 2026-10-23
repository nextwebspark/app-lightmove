package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.CallField;
import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.constant.ScorecardReportSection;
import app.lightmove.api.candidate.model.CallHeldFields;
import app.lightmove.api.candidate.model.CallReference;
import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.CandidateCompensation;
import app.lightmove.api.candidate.model.CandidateScorecard;
import app.lightmove.api.candidate.model.CandidateTag;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.model.ProfileFromCall;
import app.lightmove.api.candidate.model.RelocationTagging;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.candidate.repository.CandidateScorecardRepository;
import app.lightmove.api.candidate.repository.CandidateTagRepository;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.project.repository.ProjectRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code notetaker}'s way into the person's record for Suggest changes: what is held now, and each change a
 * researcher applies from a recorded call or undoes, written through the record's own methods with one
 * {@code UPDATED_FROM_CALL} line naming who, which fields and which call. Nothing here depends back on {@code notetaker}.
 */
@Service
@RequiredArgsConstructor
public class CandidateCallUpdates {

    /** The starter tag a call's "happy to relocate" maps to; a workspace that renamed or retired it is offered none. */
    static final String OPEN_TO_RELOCATE = "Open to relocate";

    private static final String PERSON_TARGET = "person";
    private static final DateTimeFormatter CALL_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);

    private final ProjectRepository projects;
    private final CandidateRepository candidates;
    private final CandidateScorecardRepository scorecards;
    private final CandidateTagRepository tags;
    private final PersonActivityRecorder activity;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public CallHeldFields heldOf(UUID workspaceId, UUID projectId, UUID candidateId) {
        Candidate candidate = requireCandidate(workspaceId, projectId, candidateId);
        Person person = candidate.getPerson();
        CandidateCompensation pay = person.compensation();
        UUID relocationTag = liveRelocationTag(workspaceId).map(CandidateTag::getId).orElse(null);
        String motivation = scorecards.findByCandidateIdAndWorkspaceId(candidate.getId(), workspaceId)
                .map(scorecard -> scorecard.getReport().textOf(ScorecardReportSection.MOTIVATION))
                .orElse(null);
        return new CallHeldFields(pay.noticePeriod(), pay.currency(), pay.baseSalary(), pay.bonus(), pay.allowances(),
                pay.longTermIncentive(), person.getLocationCity(), person.getLocationCountry(), motivation,
                relocationTag, relocationTag != null && person.getTagIds().contains(relocationTag));
    }

    /**
     * Writes the named fields and answers what they held before, for an undo. A package in another currency than the
     * one on file is refused while a figure it would not replace is still held in the old one: relabelling that
     * figure's currency would restate somebody else's number.
     */
    @Transactional
    public ProfileFromCall applyProfile(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                        ProfileFromCall change, CallReference call, HttpServletRequest httpRequest) {
        Candidate candidate = requireCandidate(workspaceId, projectId, candidateId);
        Person person = candidate.getPerson();
        if (change.touchesPackage() && mixesCurrencies(person.compensation(), change)) {
            throw ApiException.of(ErrorCode.NOTETAKER_SUGGESTION_UNAVAILABLE);
        }
        ProfileFromCall before = person.heldFor(change);
        person.recordFromCall(change);
        line(candidate, userId, change.fields(), call, false);
        audit.projectEvent(ProjectEventType.CANDIDATE_UPDATED, userId, workspaceId, projectId, httpRequest)
                .detail("candidateId", candidateId.toString())
                .detail("via", "CALL")
                .detail("recordingId", call.recordingId().toString())
                .record();
        return before;
    }

    /** Puts back what the fields held, but only while they still hold what the call wrote: a later edit stands. */
    @Transactional
    public void revertProfile(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                              ProfileFromCall before, ProfileFromCall applied, CallReference call,
                              HttpServletRequest httpRequest) {
        Candidate candidate = requireCandidate(workspaceId, projectId, candidateId);
        Person person = candidate.getPerson();
        if (!sameFields(person.heldFor(applied), applied)) {
            throw ApiException.of(ErrorCode.NOTETAKER_SUGGESTION_UNAVAILABLE);
        }
        person.recordFromCall(before);
        line(candidate, userId, applied.fields(), call, true);
        audit.projectEvent(ProjectEventType.CANDIDATE_UPDATED, userId, workspaceId, projectId, httpRequest)
                .detail("candidateId", candidateId.toString())
                .detail("via", "CALL")
                .detail("recordingId", call.recordingId().toString())
                .detail("reverted", "true")
                .record();
    }

    /**
     * Adds the call's words below whatever the Motivation section holds, never over it, and answers the section's
     * text before and after so an undo can tell whether anyone wrote there since.
     */
    @Transactional
    public SectionChange appendToMotivation(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                            CallField field, String text, CallReference call,
                                            HttpServletRequest httpRequest) {
        Candidate candidate = requireCandidate(workspaceId, projectId, candidateId);
        CandidateScorecard scorecard = scorecards.findByCandidateIdAndWorkspaceId(candidate.getId(), workspaceId)
                .orElseGet(() -> CandidateScorecard.startFor(candidate, workspaceId));
        String previous = scorecard.getReport().textOf(ScorecardReportSection.MOTIVATION);
        String block = "From the call on " + CALL_DATE.format(call.startsAt()) + ": " + text.strip();
        String next = previous == null || previous.isBlank() ? block : previous.stripTrailing() + "\n\n" + block;
        if (next.length() > CandidateScorecard.MAX_REPORT_SECTION) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "The Motivation section is full. Shorten it on the Assessment tab, then apply this again.");
        }
        scorecard.writeSection(ScorecardReportSection.MOTIVATION, next, userId);
        scorecards.saveAndFlush(scorecard);
        line(candidate, userId, List.of(field), call, false);
        audit.projectEvent(ProjectEventType.CANDIDATE_ASSESSMENT_SAVED, userId, workspaceId, projectId, httpRequest)
                .detail("candidateId", candidateId.toString())
                .detail("via", "CALL")
                .detail("recordingId", call.recordingId().toString())
                .record();
        return new SectionChange(previous, next);
    }

    @Transactional
    public void revertMotivation(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId, CallField field,
                                 SectionChange applied, CallReference call, HttpServletRequest httpRequest) {
        Candidate candidate = requireCandidate(workspaceId, projectId, candidateId);
        CandidateScorecard scorecard = scorecards.findByCandidateIdAndWorkspaceId(candidate.getId(), workspaceId)
                .filter(held -> Objects.equals(held.getReport().textOf(ScorecardReportSection.MOTIVATION),
                        applied.after()))
                .orElseThrow(() -> ApiException.of(ErrorCode.NOTETAKER_SUGGESTION_UNAVAILABLE));
        scorecard.writeSection(ScorecardReportSection.MOTIVATION, applied.before(), userId);
        scorecards.saveAndFlush(scorecard);
        line(candidate, userId, List.of(field), call, true);
        audit.projectEvent(ProjectEventType.CANDIDATE_ASSESSMENT_SAVED, userId, workspaceId, projectId, httpRequest)
                .detail("candidateId", candidateId.toString())
                .detail("via", "CALL")
                .detail("recordingId", call.recordingId().toString())
                .detail("reverted", "true")
                .record();
    }

    /** The workspace's live "Open to relocate" tag on the person; someone already holding it keeps it as it was. */
    @Transactional
    public RelocationTagging tagOpenToRelocate(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                               CallReference call, HttpServletRequest httpRequest) {
        Candidate candidate = requireCandidate(workspaceId, projectId, candidateId);
        CandidateTag tag = liveRelocationTag(workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOTETAKER_SUGGESTION_UNAVAILABLE));
        Person person = candidate.getPerson();
        boolean added = person.tag(tag.getId());
        if (added) {
            activity.record(person, projectId, userId, PersonActivityKind.TAGGED,
                    PersonActivityDetails.of("tagId", tag.getId()).and("tag", tag.getLabel()));
            audit.event(ProjectEventType.PERSON_TAGGED).actor(userId).workspace(workspaceId)
                    .target(PERSON_TARGET, person.getId()).from(httpRequest)
                    .detail("tagId", tag.getId().toString())
                    .detail("via", "CALL")
                    .detail("recordingId", call.recordingId().toString())
                    .record();
        }
        line(candidate, userId, List.of(CallField.OPEN_TO_RELOCATE), call, false);
        return new RelocationTagging(tag.getId(), added);
    }

    @Transactional
    public void untagOpenToRelocate(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                    RelocationTagging applied, CallReference call, HttpServletRequest httpRequest) {
        Candidate candidate = requireCandidate(workspaceId, projectId, candidateId);
        Person person = candidate.getPerson();
        if (applied.added() && person.untag(applied.tagId())) {
            activity.record(person, projectId, userId, PersonActivityKind.UNTAGGED,
                    PersonActivityDetails.of("tagId", applied.tagId()).and("tag", OPEN_TO_RELOCATE));
            audit.event(ProjectEventType.PERSON_UNTAGGED).actor(userId).workspace(workspaceId)
                    .target(PERSON_TARGET, person.getId()).from(httpRequest)
                    .detail("tagId", applied.tagId().toString())
                    .detail("via", "CALL")
                    .detail("recordingId", call.recordingId().toString())
                    .record();
        }
        line(candidate, userId, List.of(CallField.OPEN_TO_RELOCATE), call, true);
    }

    /** A task filed from a call writes its own task line; this one says it came from the call. */
    @Transactional
    public void recordTaskFromCall(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                   CallReference call, boolean reverted) {
        line(requireCandidate(workspaceId, projectId, candidateId), userId, List.of(CallField.TASK), call,
                reverted);
    }

    /** A section's text before and after a call's words went in. */
    public record SectionChange(String before, String after) {}

    private void line(Candidate candidate, UUID userId, Collection<CallField> fields, CallReference call,
                      boolean reverted) {
        String keys = fields.stream().sorted().map(CallField::value).collect(Collectors.joining(","));
        activity.record(candidate, userId, PersonActivityKind.UPDATED_FROM_CALL, PersonActivityDetails
                .of("fields", keys)
                .and("call", call.title())
                .and("callAt", call.startsAt().toString())
                .and("recordingId", call.recordingId())
                .and("reverted", reverted ? true : null));
    }

    private Optional<CandidateTag> liveRelocationTag(UUID workspaceId) {
        return tags.findByLabel(workspaceId, OPEN_TO_RELOCATE).filter(tag -> !tag.isRetired());
    }

    private static boolean mixesCurrencies(CandidateCompensation held, ProfileFromCall change) {
        if (held.currency() == null || held.currency().equalsIgnoreCase(change.currency())) {
            return false;
        }
        return (held.baseSalary() != null && !change.fields().contains(CallField.BASE_SALARY))
                || (held.bonus() != null && !change.fields().contains(CallField.BONUS))
                || (held.allowances() != null && !change.fields().contains(CallField.ALLOWANCES))
                || held.longTermIncentive() != null;
    }

    private static boolean sameFields(ProfileFromCall held, ProfileFromCall applied) {
        for (CallField field : applied.fields()) {
            boolean same = switch (field) {
                case NOTICE_PERIOD -> Objects.equals(held.noticePeriod(), applied.noticePeriod());
                case BASE_SALARY -> Objects.equals(held.baseSalary(), applied.baseSalary());
                case BONUS -> Objects.equals(held.bonus(), applied.bonus());
                case ALLOWANCES -> Objects.equals(held.allowances(), applied.allowances());
                case LOCATION -> Objects.equals(held.city(), applied.city())
                        && Objects.equals(held.country(), applied.country());
                default -> true;
            };
            if (!same) {
                return false;
            }
        }
        return !applied.touchesPackage() || Objects.equals(held.currency(), applied.currency());
    }

    private Candidate requireCandidate(UUID workspaceId, UUID projectId, UUID candidateId) {
        projects.requireInWorkspace(projectId, workspaceId);
        return candidates.requireInProject(candidateId, projectId);
    }
}
