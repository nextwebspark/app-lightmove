package app.lightmove.api.notetaker.service;

import app.lightmove.api.candidate.model.OutreachRecipient;
import app.lightmove.api.candidate.service.CandidateOutreachService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.ratelimit.service.LlmBudget;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.notetaker.constant.NotetakerStatus;
import app.lightmove.api.notetaker.dto.FiledRecordingResponse;
import app.lightmove.api.notetaker.dto.NotetakerMeetingResponse;
import app.lightmove.api.notetaker.dto.NotetakerMeetingsResponse;
import app.lightmove.api.notetaker.dto.NotetakerTranscriptResponse;
import app.lightmove.api.notetaker.dto.TranscriptLineResponse;
import app.lightmove.api.notetaker.model.LaunchedBot;
import app.lightmove.api.notetaker.model.MeetingRecording;
import app.lightmove.api.notetaker.model.NotetakerCall;
import app.lightmove.api.notetaker.repository.MeetingRecordingRepository;
import app.lightmove.api.outreach.model.KeptMeeting;
import app.lightmove.api.outreach.service.KeptMeetings;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The Notetaker on an executive's meetings, from the drawer: whether it joins each call, where its bot has got
 * to, and Remove notetaker. A switch applies to the call — every teammate's copy of it — read through the same
 * {@link NotetakerCallEligibility} the scheduler joins by.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotetakerMeetingService {

    private final KeptMeetings meetings;
    private final CandidateOutreachService people;
    private final NotetakerCallEligibility eligibility;
    private final NotetakerSwitches switches;
    private final MeetingRecordingRepository recordings;
    private final ChainedMeetingRecorder recorders;
    private final AuditService audit;
    private final TransactionTemplate transactions;
    private final LightMoveProperties properties;
    private final MeetingNotesProcessor notesProcessor;
    private final MeetingNotesWorker notesWorker;
    private final LlmBudgetGuard llmBudget;
    private final MeetingPositionRule positionRule;
    private final MeetingMediaPieces pieces;
    private final UserRepository users;
    private final Clock clock;

    @Transactional(readOnly = true)
    public NotetakerMeetingsResponse of(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId) {
        OutreachRecipient recipient = requireRecipient(workspaceId, projectId, candidateId);
        List<KeptMeeting> rows = meetings.ofPerson(workspaceId, recipient.personId());
        Map<String, NotetakerCall> calls = callsAround(workspaceId, rows);
        Map<String, MeetingRecording> recordingsByCall = calls.isEmpty() ? Map.of()
                : recordings.findByWorkspaceIdAndCallKeyIn(workspaceId, calls.keySet()).stream()
                        .collect(Collectors.toMap(MeetingRecording::getCallKey, Function.identity()));
        List<NotetakerMeetingResponse> responses = rows.stream()
                .map(row -> responseOf(row, projectId, calls, recordingsByCall))
                .toList();
        List<FiledRecordingResponse> filed = recordings
                .findByWorkspaceIdAndPersonIdAndNoteIdIsNotNull(workspaceId, recipient.personId()).stream()
                .filter(recording -> filedUnder(recording, projectId))
                .map(recording -> new FiledRecordingResponse(recording.getId(), recording.getNoteId(),
                        recording.stoppedShortAt()))
                .toList();
        return new NotetakerMeetingsResponse(properties.notetaker().enabled(), switches.workspaceAllows(workspaceId),
                switches.userAllows(userId, workspaceId), responses, filed);
    }

    /**
     * Switching off a call whose bot is already sent — the scheduler sends it ahead of the start — is Remove
     * notetaker too, or the opt-out would only stop the next bot.
     */
    public void setEnabled(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId, UUID meetingId,
                           boolean enabled, HttpServletRequest httpRequest) {
        requireOffered();
        KeptMeeting meeting = requireMeeting(workspaceId, projectId, candidateId, meetingId);
        Instant now = clock.instant();
        if (!meeting.startsAt().isAfter(now)) {
            throw ApiException.of(ErrorCode.NOTETAKER_MEETING_STARTED);
        }
        NotetakerCall call = requireCallOf(meeting);
        Optional<LaunchedBot> told = transactions.execute(status -> {
            switches.setMeeting(call.copies(), enabled, userId);
            audit.projectEvent(ProjectEventType.NOTETAKER_MEETING_SWITCHED, userId, workspaceId, projectId,
                            httpRequest)
                    .detail("candidateId", candidateId.toString())
                    .detail("meetingId", meetingId.toString())
                    .detail("enabled", String.valueOf(enabled))
                    .record();
            if (enabled) {
                return Optional.<LaunchedBot>empty();
            }
            return recordingInCall(workspaceId, call.callKey())
                    .map(recording -> requestRemoval(recording, userId, workspaceId, projectId, candidateId, now,
                            httpRequest))
                    .orElse(Optional.empty());
        });
        tellToLeave(told);
    }

    public void remove(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId, UUID meetingId,
                       HttpServletRequest httpRequest) {
        requireOffered();
        KeptMeeting meeting = requireMeeting(workspaceId, projectId, candidateId, meetingId);
        Instant now = clock.instant();
        Optional<LaunchedBot> told = transactions.execute(status -> {
            MeetingRecording recording = recordingInCall(workspaceId, NotetakerCallEligibility.callKeyOf(meeting))
                    .orElseThrow(() -> ApiException.of(ErrorCode.NOTETAKER_NOT_IN_CALL));
            return requestRemoval(recording, userId, workspaceId, projectId, candidateId, now, httpRequest);
        });
        tellToLeave(told);
    }

    /** Our bot hears it on its next heartbeat; Recall's is told at once, once the request is on file. */
    private Optional<LaunchedBot> requestRemoval(MeetingRecording recording, UUID userId, UUID workspaceId,
                                                 UUID projectId, UUID candidateId, Instant now,
                                                 HttpServletRequest httpRequest) {
        recording.requestRemoval(userId, now);
        audit.projectEvent(ProjectEventType.NOTETAKER_REMOVED, userId, workspaceId, projectId, httpRequest)
                .detail("candidateId", candidateId.toString())
                .detail("recordingId", recording.getId().toString())
                .record();
        return Optional.ofNullable(recording.getExternalId())
                .map(externalId -> new LaunchedBot(recording.getRecorder(), externalId, null));
    }

    /**
     * A call's transcript, read from the executive's drawer on a position by the recording — never through its meeting
     * row, which a calendar read may delete while the note stands. A call filed under another position reads as not
     * found here, as that position's note does.
     */
    public NotetakerTranscriptResponse transcript(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                                  UUID recordingId, HttpServletRequest httpRequest) {
        OutreachRecipient recipient = requireRecipient(workspaceId, projectId, candidateId);
        MeetingRecording recording = recordings.findById(recordingId)
                .filter(found -> found.getWorkspaceId().equals(workspaceId))
                .filter(found -> recipient.personId().equals(found.getPersonId()))
                .filter(found -> found.getJoinedAt() != null)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        requireReadable(recording, userId, projectId);
        List<TranscriptLineResponse> lines = notesProcessor.transcriptOf(recording.getId()).stream()
                .map(line -> new TranscriptLineResponse(line.at(), line.speaker(), line.text(), line.named()))
                .toList();
        audit.projectEvent(ProjectEventType.NOTETAKER_TRANSCRIPT_READ, userId, workspaceId, projectId, httpRequest)
                .detail("candidateId", candidateId.toString())
                .detail("recordingId", recording.getId().toString())
                .record();
        String consultant = users.findById(recording.getUserId()).map(User::getFullName).orElse(null);
        return new NotetakerTranscriptResponse(recording.getId(), recording.getStatus(),
                recording.getStatus() == NotetakerStatus.DONE, recording.getTitle(), recording.getStartsAt(),
                recording.getEndsAt(), recording.getJoinedAt(), consultant, recording.stoppedShortAt(), lines);
    }

    /** Try again, for a call whose note could not be made: a press, metered like any other model press. */
    public void retry(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId, UUID meetingId,
                      HttpServletRequest httpRequest) {
        requireOffered();
        MeetingRecording found = requireRecordingOf(userId, workspaceId, projectId, candidateId, meetingId);
        // Past the retention its pieces are gone, and a try would spend a unit to file nothing.
        if (!found.isRetryable() || !pieces.holdsSpeech(found.getId())) {
            throw ApiException.of(ErrorCode.NOTETAKER_NOT_RETRYABLE);
        }
        llmBudget.require(LlmBudget.MEETING_SUMMARY, userId);
        transactions.executeWithoutResult(status -> {
            MeetingRecording recording = recordings.findById(found.getId())
                    .filter(MeetingRecording::isRetryable)
                    .orElseThrow(() -> ApiException.of(ErrorCode.NOTETAKER_NOT_RETRYABLE));
            recording.retryProcessing();
            audit.projectEvent(ProjectEventType.NOTETAKER_RETRIED, userId, workspaceId, projectId, httpRequest)
                    .detail("candidateId", candidateId.toString())
                    .detail("recordingId", recording.getId().toString())
                    .record();
        });
        notesWorker.workOn(found.getId());
    }

    /**
     * A recorded call read from {@code projectId}: the position its note was filed under, or — until it is — the one
     * the position rule gives it. A call the rule gives no position is its consultant's alone until its note is filed
     * about the person; read from any other position it is not found, as a note filed there would be.
     */
    private MeetingRecording requireRecordingOf(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                                UUID meetingId) {
        KeptMeeting meeting = requireMeeting(workspaceId, projectId, candidateId, meetingId);
        if (meeting.joinUrl() == null) {
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }
        MeetingRecording recording = recordings.findByWorkspaceIdAndCallKey(workspaceId,
                        NotetakerCallEligibility.callKeyOf(meeting))
                .filter(found -> found.getJoinedAt() != null)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        requireReadable(recording, userId, projectId);
        return recording;
    }

    private void requireReadable(MeetingRecording recording, UUID userId, UUID projectId) {
        boolean filed = recording.getNoteId() != null;
        UUID belongsTo = filed ? recording.getProjectId()
                : transactions.execute(status -> positionRule.positionOf(recording)).orElse(null);
        boolean readable = belongsTo != null ? belongsTo.equals(projectId)
                : filed || recording.getUserId().equals(userId);
        if (!readable) {
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }
    }

    /** Its note is filed under this position, or about the person, which every position reads. */
    private static boolean filedUnder(MeetingRecording recording, UUID projectId) {
        return recording.getNoteId() != null
                && (recording.getProjectId() == null || projectId.equals(recording.getProjectId()));
    }

    private void tellToLeave(Optional<LaunchedBot> bot) {
        bot.ifPresent(sent -> {
            try {
                recorders.requestLeave(sent.recorder(), sent.externalId());
            } catch (RuntimeException failed) {
                log.warn("Notetaker {} could not be told to leave; its heartbeat will", sent.externalId(), failed);
            }
        });
    }

    private Optional<MeetingRecording> recordingInCall(UUID workspaceId, String callKey) {
        return recordings.findByWorkspaceIdAndCallKey(workspaceId, callKey)
                .filter(recording -> recording.getStatus().isInCall());
    }

    /** The calls these rows are copies of, with every teammate's copy found at the same start. */
    private Map<String, NotetakerCall> callsAround(UUID workspaceId, List<KeptMeeting> rows) {
        List<Instant> starts = rows.stream()
                .filter(row -> row.joinUrl() != null)
                .map(KeptMeeting::startsAt)
                .distinct()
                .toList();
        return eligibility.callsOf(meetings.inWorkspaceStartingAt(workspaceId, starts)).stream()
                .collect(Collectors.toMap(NotetakerCall::callKey, Function.identity(), (first, second) -> first));
    }

    private NotetakerCall requireCallOf(KeptMeeting meeting) {
        if (meeting.joinUrl() == null) {
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }
        return callsAround(meeting.workspaceId(), List.of(meeting)).get(NotetakerCallEligibility.callKeyOf(meeting));
    }

    private static NotetakerMeetingResponse responseOf(KeptMeeting row, UUID projectId,
                                                       Map<String, NotetakerCall> calls,
                                                       Map<String, MeetingRecording> recordingsByCall) {
        String callKey = row.joinUrl() == null ? null : NotetakerCallEligibility.callKeyOf(row);
        NotetakerCall call = callKey == null ? null : calls.get(callKey);
        MeetingRecording recording = callKey == null ? null : recordingsByCall.get(callKey);
        boolean filed = recording != null && recording.getNoteId() != null;
        boolean filedHere = recording != null && filedUnder(recording, projectId);
        return new NotetakerMeetingResponse(row.id(), call == null ? null : call.platform(),
                call != null && call.joinable(),
                call == null || call.meetingAllows(),
                recording == null ? null : recording.getId(),
                recording == null ? null : recording.getStatus(),
                recording == null ? null : recording.getEndReason(),
                recording == null ? null : recording.getRecorder(),
                recording == null ? null : recording.getJoinedAt(),
                recording == null ? null : recording.stoppedShortAt(),
                filedHere, filedHere ? recording.getNoteId() : null, filed && !filedHere);
    }

    private KeptMeeting requireMeeting(UUID workspaceId, UUID projectId, UUID candidateId, UUID meetingId) {
        OutreachRecipient recipient = requireRecipient(workspaceId, projectId, candidateId);
        return meetings.find(workspaceId, meetingId)
                .filter(meeting -> meeting.personId().equals(recipient.personId()))
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private OutreachRecipient requireRecipient(UUID workspaceId, UUID projectId, UUID candidateId) {
        return people.currentRecipient(workspaceId, projectId, candidateId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private void requireOffered() {
        if (!properties.notetaker().enabled()) {
            throw ApiException.of(ErrorCode.NOTETAKER_UNAVAILABLE);
        }
    }
}
