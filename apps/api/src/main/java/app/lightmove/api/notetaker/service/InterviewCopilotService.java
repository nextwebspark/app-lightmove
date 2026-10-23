package app.lightmove.api.notetaker.service;

import app.lightmove.api.billing.usage.constant.UsageKind;
import app.lightmove.api.billing.usage.service.FairUseGuard;
import app.lightmove.api.candidate.model.OutreachRecipient;
import app.lightmove.api.candidate.service.CandidateCallUpdates;
import app.lightmove.api.candidate.service.CandidateOutreachService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.ProjectAccess;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.notetaker.constant.CoverageState;
import app.lightmove.api.notetaker.constant.LiveCopilotStatus;
import app.lightmove.api.notetaker.constant.NotetakerStatus;
import app.lightmove.api.notetaker.constant.NudgeStatus;
import app.lightmove.api.notetaker.constant.RecordGap;
import app.lightmove.api.notetaker.dto.CopilotCoverageResponse;
import app.lightmove.api.notetaker.dto.CopilotGapResponse;
import app.lightmove.api.notetaker.dto.CopilotNudgeResponse;
import app.lightmove.api.notetaker.dto.CopilotQuestionResponse;
import app.lightmove.api.notetaker.dto.CopilotResponse;
import app.lightmove.api.notetaker.dto.CopilotViewerResponse;
import app.lightmove.api.notetaker.dto.LiveLineResponse;
import app.lightmove.api.notetaker.dto.NotetakerRecordingResponse;
import app.lightmove.api.notetaker.model.CompetencyCoverage;
import app.lightmove.api.notetaker.model.GapHeard;
import app.lightmove.api.notetaker.model.InterviewPlan;
import app.lightmove.api.notetaker.model.InterviewQuestion;
import app.lightmove.api.notetaker.model.LiveCoaching;
import app.lightmove.api.notetaker.model.MeetingLiveLine;
import app.lightmove.api.notetaker.model.MeetingRecording;
import app.lightmove.api.notetaker.repository.MeetingLiveLineRepository;
import app.lightmove.api.notetaker.repository.MeetingRecordingRepository;
import app.lightmove.api.position.model.BriefCompetency;
import app.lightmove.api.position.service.PositionService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The live copilot of a Notetaker recording, as the drawer and the copilot window drive it: switched on or off before
 * the bot joins (Recall streams a transcript only to a bot made for it), read by any window on the position, steered by
 * hand (a nudge used or dismissed, a prepared question ticked), and stopped for everyone by whoever switched it on, a
 * lead or an admin. Everything here is staff-only and read only from the position that sent the bot.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewCopilotService {

    private final MeetingRecordingRepository recordings;
    private final MeetingLiveLineRepository lines;
    private final CandidateOutreachService people;
    private final CandidateCallUpdates updates;
    private final PositionService positions;
    private final InterviewPlanService plans;
    private final NotetakerService notetaker;
    private final RecallBotApi recall;
    private final FairUseGuard fairUse;
    private final CopilotStreamRegistry streams;
    private final CopilotStreamPublisher publisher;
    private final CopilotViewers viewers;
    private final TeamVoices teamVoices;
    private final ProjectAccess projectAccess;
    private final WorkspaceAccess workspaceAccess;
    private final UserRepository users;
    private final AuditService audit;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /** Before the bot joins only; the bot is changed at Recall first, and the row only once Recall took it. */
    public NotetakerRecordingResponse switchLive(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                                 UUID recordingId, boolean enabled, HttpServletRequest httpRequest) {
        OutreachRecipient recipient = requireRecipient(workspaceId, projectId, candidateId);
        MeetingRecording recording = transactions.execute(status ->
                requireRecording(workspaceId, projectId, recipient, recordingId));
        if (recording.isLiveCopilot() == enabled) {
            return NotetakerService.responseOf(recording);
        }
        requireSwitchable(recording);
        if (enabled) {
            fairUse.check(workspaceId, userId, UsageKind.AI_ENRICH, 1);
        }
        try {
            recall.updateLive(recording.getRecallBotId(), enabled ? notetaker.liveTranscriptUrl() : null);
        } catch (VendorException refused) {
            log.warn("Recall refused to change the live copilot of recording {}: {}", recordingId,
                    refused.getMessage());
            throw ApiException.of(refused.getKind() == VendorFailureKind.BAD_REQUEST
                    || refused.getKind() == VendorFailureKind.GONE
                    ? ErrorCode.NOTETAKER_COPILOT_LOCKED : ErrorCode.NOTETAKER_SEND_FAILED);
        }
        return transactions.execute(status -> {
            MeetingRecording held = recordings.lockForDecision(recordingId, workspaceId, projectId,
                    recipient.personId()).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
            held.liveCopilot(enabled, userId);
            audit.projectEvent(enabled ? ProjectEventType.NOTETAKER_COPILOT_ENABLED
                            : ProjectEventType.NOTETAKER_COPILOT_DISABLED, userId, workspaceId, projectId, httpRequest)
                    .detail("candidateId", candidateId.toString())
                    .detail("recordingId", recordingId.toString())
                    .record();
            return NotetakerService.responseOf(held);
        });
    }

    @Transactional(readOnly = true)
    public CopilotResponse read(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId, UUID recordingId,
                                long afterSeq) {
        OutreachRecipient recipient = requireRecipient(workspaceId, projectId, candidateId);
        MeetingRecording recording = requireCopilot(requireRecording(workspaceId, projectId, recipient, recordingId));
        List<BriefCompetency> competencies = positions.competenciesOf(workspaceId, projectId).competencies();
        LiveCoaching reading = recording.getLive() == null ? LiveCoaching.empty() : recording.getLive();
        Set<String> team = teamVoices.namesOf(workspaceId, teamOf(recording));
        List<InterviewQuestion> plan = plans.planOfCall(workspaceId, projectId, recipient.personId(),
                        recording.getStartsAt(), recording.getTitle())
                .map(InterviewPlan::includedQuestions)
                .orElse(List.of());
        List<MeetingLiveLine> finished = lines.findByRecordingIdAndPartialFalseAndSeqGreaterThanOrderBySeqAsc(
                recordingId, Math.max(0, afterSeq));
        long lastSeq = finished.isEmpty() ? Math.max(0, afterSeq) : finished.getLast().getSeq();
        return new CopilotResponse(recording.getId(), recording.getTitle(), recipient.fullName(),
                recording.getStartsAt(), recording.getStatus(), recording.getLiveStatus(), reading.coach(),
                nameOf(recording.getLiveStoppedBy()), mayStop(userId, workspaceId, projectId, recording),
                coverageOf(competencies, reading), gapsOf(missingNow(workspaceId, projectId, recipient), reading),
                plan.stream()
                        .map(question -> new CopilotQuestionResponse(question.id(), question.section(),
                                question.competency(), question.text(), reading.asked().contains(question.id())))
                        .toList(),
                reading.nudges().stream()
                        .filter(nudge -> nudge.status() == NudgeStatus.SHOWN)
                        .map(nudge -> new CopilotNudgeResponse(nudge.id(), nudge.text(), nudge.reason()))
                        .toList(),
                finished.stream().map(line -> lineOf(line, team)).toList(),
                lines.findByRecordingIdAndPartialTrue(recordingId).stream().map(line -> lineOf(line, team)).toList(),
                lastSeq, viewersOf(userId, recordingId));
    }

    /**
     * A window's stream. Each (re)connect stamps who is watching and tells every other window, so their headers show
     * the same people; the first in a while is audited, since a teammate reading a live call is worth a line.
     */
    public SseEmitter subscribe(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId, UUID recordingId,
                                HttpServletRequest httpRequest) {
        OutreachRecipient recipient = requireRecipient(workspaceId, projectId, candidateId);
        transactions.executeWithoutResult(status -> {
            MeetingRecording recording = requireCopilot(requireRecording(workspaceId, projectId, recipient,
                    recordingId));
            Instant now = clock.instant();
            boolean wasWatching = viewers.watching(recordingId, now).contains(userId);
            viewers.seen(recordingId, userId, now);
            if (!wasWatching) {
                audit.projectEvent(ProjectEventType.NOTETAKER_COPILOT_OPENED, userId, workspaceId, projectId,
                                httpRequest)
                        .detail("candidateId", candidateId.toString())
                        .detail("recordingId", recording.getId().toString())
                        .record();
                publisher.publish(recordingId);
            }
        });
        return streams.subscribe(recordingId);
    }

    /** Stops coaching for everyone; the bot keeps recording and the notes are filed after the call as ever. */
    public CopilotResponse stop(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId, UUID recordingId,
                                HttpServletRequest httpRequest) {
        OutreachRecipient recipient = requireRecipient(workspaceId, projectId, candidateId);
        transactions.executeWithoutResult(status -> {
            MeetingRecording recording = requireCopilot(recordings.lockForDecision(recordingId, workspaceId,
                    projectId, recipient.personId()).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND)));
            if (!mayStop(userId, workspaceId, projectId, recording)) {
                throw ApiException.of(ErrorCode.NOTETAKER_COPILOT_NOT_YOURS);
            }
            if (recording.getLiveStatus() == LiveCopilotStatus.STOPPED) {
                return;
            }
            recording.liveStopped(userId);
            lines.deleteByRecording(recordingId);
            audit.projectEvent(ProjectEventType.NOTETAKER_COPILOT_STOPPED, userId, workspaceId, projectId,
                            httpRequest)
                    .detail("candidateId", candidateId.toString())
                    .detail("recordingId", recordingId.toString())
                    .record();
            publisher.publish(recordingId);
        });
        return read(userId, workspaceId, projectId, candidateId, recordingId, 0);
    }

    public void decideNudge(UUID workspaceId, UUID projectId, UUID candidateId, UUID recordingId, String nudgeId,
                            NudgeStatus decision) {
        steer(workspaceId, projectId, candidateId, recordingId, reading -> reading.withNudges(reading.nudges().stream()
                .map(nudge -> nudge.id().equals(nudgeId) && nudge.status() == NudgeStatus.SHOWN
                        ? nudge.as(decision) : nudge)
                .toList()));
    }

    public void markAsked(UUID workspaceId, UUID projectId, UUID candidateId, UUID recordingId, String questionId,
                          boolean asked) {
        steer(workspaceId, projectId, candidateId, recordingId, reading -> {
            Set<String> next = new LinkedHashSet<>(reading.asked());
            if (asked) {
                next.add(questionId);
            } else {
                next.remove(questionId);
            }
            return reading.withAsked(List.copyOf(next));
        });
    }

    private void steer(UUID workspaceId, UUID projectId, UUID candidateId, UUID recordingId,
                       Function<LiveCoaching, LiveCoaching> change) {
        OutreachRecipient recipient = requireRecipient(workspaceId, projectId, candidateId);
        transactions.executeWithoutResult(status -> {
            MeetingRecording recording = requireCopilot(recordings.lockForDecision(recordingId, workspaceId,
                    projectId, recipient.personId()).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND)));
            recording.liveCoaching(change.apply(recording.getLive() == null ? LiveCoaching.empty()
                    : recording.getLive()));
            publisher.publish(recordingId);
        });
    }

    static List<CopilotCoverageResponse> coverageOf(List<BriefCompetency> competencies, LiveCoaching reading) {
        Map<String, CompetencyCoverage> byName = reading.coverage().stream()
                .collect(Collectors.toMap(line -> InterviewPlanner.keyOf(line.competency()), Function.identity(),
                        (first, second) -> first, LinkedHashMap::new));
        return competencies.stream()
                .map(competency -> {
                    CompetencyCoverage line = byName.get(InterviewPlanner.keyOf(competency.name()));
                    return new CopilotCoverageResponse(competency.panel(), competency.name(), competency.weight(),
                            line == null ? CoverageState.NOT_YET : line.state(),
                            line == null ? null : line.quote(), line == null ? null : line.at());
                })
                .toList();
    }

    static List<CopilotGapResponse> gapsOf(List<RecordGap> missing, LiveCoaching reading) {
        Map<RecordGap, GapHeard> heard = reading.gaps().stream()
                .collect(Collectors.toMap(GapHeard::gap, Function.identity(), (first, second) -> first));
        Set<RecordGap> shown = new LinkedHashSet<>(missing);
        shown.addAll(heard.keySet());
        List<CopilotGapResponse> gaps = new ArrayList<>();
        for (RecordGap gap : RecordGap.values()) {
            if (shown.contains(gap)) {
                GapHeard spoken = heard.get(gap);
                gaps.add(new CopilotGapResponse(gap, spoken != null, spoken == null ? null : spoken.at()));
            }
        }
        return gaps;
    }

    private List<RecordGap> missingNow(UUID workspaceId, UUID projectId, OutreachRecipient recipient) {
        return InterviewPlanService.missingOf(updates.heldOf(workspaceId, projectId, recipient.candidateId()));
    }

    private List<CopilotViewerResponse> viewersOf(UUID userId, UUID recordingId) {
        List<UUID> watching = viewers.watching(recordingId, clock.instant());
        Map<UUID, String> names = users.findAllById(watching).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName, (first, second) -> first));
        return watching.stream()
                .map(viewer -> new CopilotViewerResponse(viewer, names.get(viewer), viewer.equals(userId)))
                .toList();
    }

    private boolean mayStop(UUID userId, UUID workspaceId, UUID projectId, MeetingRecording recording) {
        return userId.equals(recording.getLiveEnabledBy()) || userId.equals(recording.getRequestedBy())
                || projectAccess.allows(userId, workspaceId, projectId, ProjectAction.TEAM_MANAGE)
                || workspaceAccess.holdsAction(userId, workspaceId, WorkspaceAction.WORKSPACE_MANAGE);
    }

    static List<UUID> teamOf(MeetingRecording recording) {
        List<UUID> team = new ArrayList<>();
        team.add(recording.getRequestedBy());
        if (recording.getLiveEnabledBy() != null) {
            team.add(recording.getLiveEnabledBy());
        }
        return team;
    }

    private static LiveLineResponse lineOf(MeetingLiveLine line, Set<String> team) {
        return new LiveLineResponse(line.getSeq(), line.getSaidAt(), line.getSpeaker(), line.getText(),
                TeamVoices.isTeam(team, line.getSpeaker(), line.isNamed()));
    }

    private String nameOf(UUID userId) {
        return userId == null ? null : users.findById(userId).map(User::getFullName).orElse(null);
    }

    private static void requireSwitchable(MeetingRecording recording) {
        if (recording.getStatus() != NotetakerStatus.SCHEDULED || recording.getRecallBotId() == null) {
            throw ApiException.of(ErrorCode.NOTETAKER_COPILOT_LOCKED);
        }
    }

    private static MeetingRecording requireCopilot(MeetingRecording recording) {
        if (!recording.isLiveCopilot()) {
            throw ApiException.of(ErrorCode.NOTETAKER_COPILOT_OFF);
        }
        return recording;
    }

    private MeetingRecording requireRecording(UUID workspaceId, UUID projectId, OutreachRecipient recipient,
                                              UUID recordingId) {
        return recordings.findByIdAndWorkspaceIdAndProjectIdAndPersonId(recordingId, workspaceId, projectId,
                recipient.personId()).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private OutreachRecipient requireRecipient(UUID workspaceId, UUID projectId, UUID candidateId) {
        return people.currentRecipient(workspaceId, projectId, candidateId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }
}
