package app.lightmove.api.notetaker.service;

import app.lightmove.api.billing.usage.constant.UsageKind;
import app.lightmove.api.billing.usage.service.FairUseGuard;
import app.lightmove.api.candidate.model.OutreachRecipient;
import app.lightmove.api.candidate.service.CandidateOutreachService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.ratelimit.service.LlmBudget;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.notetaker.constant.NotetakerStatus;
import app.lightmove.api.notetaker.dto.CandidateNotetakerResponse;
import app.lightmove.api.notetaker.dto.NotetakerRecordingResponse;
import app.lightmove.api.notetaker.dto.NotetakerTranscriptResponse;
import app.lightmove.api.notetaker.dto.TranscriptLineResponse;
import app.lightmove.api.notetaker.model.MeetingRecording;
import app.lightmove.api.notetaker.model.MeetingTranscriptReady;
import app.lightmove.api.notetaker.model.RecallBotRequest;
import app.lightmove.api.notetaker.repository.MeetingRecordingRepository;
import app.lightmove.api.outreach.model.KeptMeeting;
import app.lightmove.api.outreach.service.KeptMeetings;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The Notetaker as a consultant drives it from the executive drawer: send a Recall.ai bot to a call with the person,
 * call it back, try a failed note again, read the transcript. The bot is claimed in our database before Recall is
 * asked, so two presses (or two teammates on one call) never send two bots, and Recall is never called inside a
 * transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotetakerService {

    static final String BOT_NAME = "Uncava Notetaker";

    /** How long before the call the bot is asked in: the lobby opens a little early on every platform. */
    static final Duration JOIN_AHEAD = Duration.ofMinutes(1);

    private final RecallBotApi recall;
    private final LlmBudgetGuard llmBudget;
    private final FairUseGuard fairUse;
    private final MeetingRecordingRepository recordings;
    private final KeptMeetings meetings;
    private final CandidateOutreachService people;
    private final UserRepository users;
    private final AuditService audit;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional(readOnly = true)
    public CandidateNotetakerResponse ofCandidate(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId) {
        OutreachRecipient recipient = requireRecipient(workspaceId, projectId, candidateId);
        List<NotetakerRecordingResponse> rows = recordings
                .findByWorkspaceIdAndProjectIdAndPersonIdOrderByStartsAtDescCreatedAtDesc(workspaceId, projectId,
                        recipient.personId()).stream()
                .map(NotetakerService::responseOf)
                .toList();
        if (!recall.isOffered() || recipient.doNotContact()) {
            return new CandidateNotetakerResponse(recall.isOffered(), rows, List.of());
        }
        Instant now = clock.instant();
        List<KeptMeeting> calls = meetings.ofPerson(workspaceId, recipient.personId());
        List<UUID> recordable = calls.stream()
                .filter(meeting -> meeting.endsAt().isAfter(now) && joinableOrNull(meeting.joinUrl()) != null)
                .filter(meeting -> calls.stream()
                        .anyMatch(copy -> copy.isSameCallAs(meeting) && copy.isHeldOrBookedBy(userId)))
                .map(KeptMeeting::id)
                .toList();
        return new CandidateNotetakerResponse(true, rows, recordable);
    }

    public NotetakerRecordingResponse schedule(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                               UUID meetingId, HttpServletRequest httpRequest) {
        if (!recall.isOffered()) {
            throw ApiException.of(ErrorCode.NOTETAKER_NOT_OFFERED);
        }
        OutreachRecipient recipient = requireRecipient(workspaceId, projectId, candidateId);
        if (recipient.doNotContact()) {
            throw ApiException.of(ErrorCode.MEETING_DO_NOT_CONTACT);
        }
        KeptMeeting meeting = meetings.of(workspaceId, recipient.personId(), meetingId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        // A recorder joins a call only for someone it is theirs to record: on their own calendar, or booked by them.
        // A teammate's copy of the same call counts, since every calendar keeps its own row of it.
        if (meetings.ofPerson(workspaceId, recipient.personId()).stream()
                .noneMatch(copy -> copy.isSameCallAs(meeting) && copy.isHeldOrBookedBy(userId))) {
            throw ApiException.of(ErrorCode.NOTETAKER_NOT_YOUR_CALL);
        }
        Instant now = clock.instant();
        if (!meeting.endsAt().isAfter(now)) {
            throw ApiException.of(ErrorCode.NOTETAKER_MEETING_OVER);
        }
        String joinUrl = joinableOrNull(meeting.joinUrl());
        if (joinUrl == null) {
            throw ApiException.of(ErrorCode.NOTETAKER_NO_JOIN_LINK);
        }
        fairUse.check(workspaceId, userId, UsageKind.AI_ENRICH, 1);
        UUID recordingId = claim(userId, workspaceId, projectId, recipient, meeting, joinUrl);
        String botId;
        try {
            botId = recall.send(new RecallBotRequest(joinUrl, joinAtFor(meeting.startsAt(), now), BOT_NAME,
                    consentLineFor(userId), Map.of("recordingId", recordingId.toString(),
                            "workspaceId", workspaceId.toString())));
        } catch (VendorException | ApiException refused) {
            log.warn("Recall refused the Notetaker for recording {}: {}", recordingId, refused.getMessage());
            transactions.executeWithoutResult(status ->
                    recordings.findById(recordingId).ifPresent(recording -> recording.failed("SEND_FAILED")));
            throw ApiException.of(ErrorCode.NOTETAKER_SEND_FAILED);
        }
        MeetingRecording sent = transactions.execute(status -> {
            MeetingRecording recording = recordings.findById(recordingId).orElseThrow();
            recording.sent(botId);
            audit.projectEvent(ProjectEventType.NOTETAKER_SCHEDULED, userId, workspaceId, projectId, httpRequest)
                    .detail("candidateId", candidateId.toString())
                    .detail("recordingId", recordingId.toString())
                    .detail("startsAt", meeting.startsAt().toString())
                    .record();
            return recording;
        });
        if (sent.getStatus() == NotetakerStatus.CANCELLED) {
            // Removed while Recall was still being asked: the bot it just made must not go on to join and record.
            removeQuietly(botId);
        }
        return responseOf(sent);
    }

    /** Anyone on the team may call a bot back: a recording nobody wanted is the thing to stop, whoever sent it. */
    public void cancel(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId, UUID recordingId,
                       HttpServletRequest httpRequest) {
        OutreachRecipient recipient = requireRecipient(workspaceId, projectId, candidateId);
        MeetingRecording recording = recordings
                .findByIdAndWorkspaceIdAndProjectIdAndPersonId(recordingId, workspaceId, projectId,
                        recipient.personId())
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        requireCancellable(recording);
        String removedBotId = recording.getRecallBotId();
        if (removedBotId != null) {
            try {
                recall.remove(removedBotId);
            } catch (VendorException failed) {
                log.warn("Recall could not call back bot {}: {}", removedBotId, failed.getMessage());
                throw ApiException.of(ErrorCode.NOTETAKER_SEND_FAILED);
            }
        }
        String botIdNow = transactions.execute(status -> {
            MeetingRecording held = recordings.findById(recordingId).orElseThrow();
            requireCancellable(held);
            held.cancelled();
            audit.projectEvent(ProjectEventType.NOTETAKER_CANCELLED, userId, workspaceId, projectId, httpRequest)
                    .detail("candidateId", candidateId.toString())
                    .detail("recordingId", recordingId.toString())
                    .record();
            return held.getRecallBotId();
        });
        if (botIdNow != null && !botIdNow.equals(removedBotId)) {
            // The send finished between the read and the cancel: that bot is called back too.
            removeQuietly(botIdNow);
        }
        if (botIdNow != null) {
            deleteMediaQuietly(botIdNow);
        }
    }

    /** Try again is a press that spends a model call, so it takes the same meter Assess with AI does. */
    @Transactional
    public NotetakerRecordingResponse retry(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                            UUID recordingId) {
        OutreachRecipient recipient = requireRecipient(workspaceId, projectId, candidateId);
        MeetingRecording recording = recordings
                .findByIdAndWorkspaceIdAndProjectIdAndPersonId(recordingId, workspaceId, projectId,
                        recipient.personId())
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (!recording.isRetryable()) {
            throw ApiException.of(ErrorCode.NOTETAKER_NOT_CHANGEABLE);
        }
        llmBudget.require(LlmBudget.CANDIDATE_AI_ENRICH, userId);
        fairUse.check(workspaceId, userId, UsageKind.AI_ENRICH, 1);
        recording.retrying();
        recordings.releaseProcessing(recording.getId());
        events.publishEvent(new MeetingTranscriptReady(recording.getId()));
        return responseOf(recording);
    }

    @Transactional(readOnly = true)
    public NotetakerTranscriptResponse transcript(UUID workspaceId, UUID projectId, UUID candidateId,
                                                  UUID recordingId) {
        OutreachRecipient recipient = requireRecipient(workspaceId, projectId, candidateId);
        MeetingRecording recording = recordings
                .findByIdAndWorkspaceIdAndProjectIdAndPersonId(recordingId, workspaceId, projectId,
                        recipient.personId())
                .filter(found -> found.getTranscript() != null)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        return new NotetakerTranscriptResponse(recording.getId(), recording.getTitle(), recording.getStartsAt(),
                recording.getTranscript().stream()
                        .map(line -> new TranscriptLineResponse(line.at(), line.speaker(), line.text()))
                        .toList());
    }

    /** Recall asks the bot in just before the call; one due within the minute goes at once. */
    static Instant joinAtFor(Instant startsAt, Instant now) {
        Instant joinAt = startsAt.minus(JOIN_AHEAD);
        return joinAt.isAfter(now.plus(JOIN_AHEAD)) ? joinAt : null;
    }

    /** Only an https link goes to Recall; a dial-in number or a room name is not something a bot can open. */
    static String joinableOrNull(String joinUrl) {
        if (joinUrl == null || joinUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(joinUrl.strip());
            return "https".equals(uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT))
                    && uri.getHost() != null && uri.getUserInfo() == null ? uri.toString() : null;
        } catch (IllegalArgumentException unreadable) {
            return null;
        }
    }

    private UUID claim(UUID userId, UUID workspaceId, UUID projectId, OutreachRecipient recipient,
                       KeptMeeting meeting, String joinUrl) {
        try {
            return transactions.execute(status -> {
                if (recordings.existsByWorkspaceIdAndJoinUrlAndStartsAtAndStatusIn(workspaceId, joinUrl,
                        meeting.startsAt(), NotetakerStatus.LIVE)) {
                    throw ApiException.of(ErrorCode.NOTETAKER_ALREADY_SCHEDULED);
                }
                return recordings.saveAndFlush(MeetingRecording.claimed(workspaceId, recipient.personId(), projectId,
                        recipient.candidateId(), meeting.id(), meeting.title(), meeting.startsAt(), meeting.endsAt(),
                        joinUrl, userId)).getId();
            });
        } catch (DataIntegrityViolationException raced) {
            throw ApiException.of(ErrorCode.NOTETAKER_ALREADY_SCHEDULED);
        }
    }

    /** Nobody is recorded without being told, and told whose notes they are. */
    private String consentLineFor(UUID userId) {
        String name = users.findById(userId).map(User::getFullName).filter(full -> !full.isBlank())
                .orElse("the consultant");
        return "Hello, I'm " + BOT_NAME + ". I'm recording and transcribing this call for " + name
                + "'s notes. Ask them to remove me at any time.";
    }

    private static void requireCancellable(MeetingRecording recording) {
        if (recording.getStatus() != NotetakerStatus.SCHEDULED && recording.getStatus() != NotetakerStatus.JOINING
                && recording.getStatus() != NotetakerStatus.RECORDING) {
            throw ApiException.of(ErrorCode.NOTETAKER_NOT_CHANGEABLE);
        }
    }

    private void removeQuietly(String botId) {
        try {
            recall.remove(botId);
        } catch (VendorException failed) {
            log.error("Recall could not call back cancelled bot {}; remove it from Recall's dashboard: {}", botId,
                    failed.getMessage());
        }
    }

    private void deleteMediaQuietly(String botId) {
        try {
            recall.deleteMedia(botId);
        } catch (VendorException failed) {
            log.warn("Recall kept the media of cancelled bot {}: {}", botId, failed.getMessage());
        }
    }

    private OutreachRecipient requireRecipient(UUID workspaceId, UUID projectId, UUID candidateId) {
        return people.currentRecipient(workspaceId, projectId, candidateId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    static NotetakerRecordingResponse responseOf(MeetingRecording recording) {
        return new NotetakerRecordingResponse(recording.getId(), recording.getMeetingId(), recording.getStartsAt(),
                recording.getTitle(), recording.getStatus(), recording.getFailureReason(),
                recording.getTranscript() != null, recording.isRetryable(), recording.getNoteId());
    }
}
