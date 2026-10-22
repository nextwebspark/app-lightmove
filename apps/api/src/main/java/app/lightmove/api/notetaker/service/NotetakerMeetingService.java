package app.lightmove.api.notetaker.service;

import app.lightmove.api.candidate.model.OutreachRecipient;
import app.lightmove.api.candidate.service.CandidateOutreachService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.notetaker.dto.NotetakerMeetingResponse;
import app.lightmove.api.notetaker.dto.NotetakerMeetingsResponse;
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
    private final MeetingRecorder recorder;
    private final AuditService audit;
    private final TransactionTemplate transactions;
    private final LightMoveProperties properties;
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
                .map(row -> responseOf(row, calls, recordingsByCall))
                .toList();
        return new NotetakerMeetingsResponse(properties.notetaker().enabled(), switches.workspaceAllows(workspaceId),
                switches.userAllows(userId, workspaceId), responses);
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
        Optional<String> told = transactions.execute(status -> {
            switches.setMeeting(call.copies(), enabled, userId);
            audit.projectEvent(ProjectEventType.NOTETAKER_MEETING_SWITCHED, userId, workspaceId, projectId,
                            httpRequest)
                    .detail("candidateId", candidateId.toString())
                    .detail("meetingId", meetingId.toString())
                    .detail("enabled", String.valueOf(enabled))
                    .record();
            if (enabled) {
                return Optional.<String>empty();
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
        Optional<String> told = transactions.execute(status -> {
            MeetingRecording recording = recordingInCall(workspaceId, NotetakerCallEligibility.callKeyOf(meeting))
                    .orElseThrow(() -> ApiException.of(ErrorCode.NOTETAKER_NOT_IN_CALL));
            return requestRemoval(recording, userId, workspaceId, projectId, candidateId, now, httpRequest);
        });
        tellToLeave(told);
    }

    /** Our bot hears it on its next heartbeat; Recall's is told at once, once the request is on file. */
    private Optional<String> requestRemoval(MeetingRecording recording, UUID userId, UUID workspaceId,
                                            UUID projectId, UUID candidateId, Instant now,
                                            HttpServletRequest httpRequest) {
        recording.requestRemoval(userId, now);
        audit.projectEvent(ProjectEventType.NOTETAKER_REMOVED, userId, workspaceId, projectId, httpRequest)
                .detail("candidateId", candidateId.toString())
                .detail("recordingId", recording.getId().toString())
                .record();
        return Optional.ofNullable(recording.getExternalId());
    }

    private void tellToLeave(Optional<String> externalId) {
        externalId.ifPresent(id -> {
            try {
                recorder.requestLeave(id);
            } catch (RuntimeException failed) {
                log.warn("Notetaker {} could not be told to leave; its heartbeat will", id, failed);
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

    private static NotetakerMeetingResponse responseOf(KeptMeeting row, Map<String, NotetakerCall> calls,
                                                       Map<String, MeetingRecording> recordingsByCall) {
        String callKey = row.joinUrl() == null ? null : NotetakerCallEligibility.callKeyOf(row);
        NotetakerCall call = callKey == null ? null : calls.get(callKey);
        MeetingRecording recording = callKey == null ? null : recordingsByCall.get(callKey);
        return new NotetakerMeetingResponse(row.id(), call == null ? null : call.platform(),
                call != null && call.joinable(),
                call == null || call.meetingAllows(),
                recording == null ? null : recording.getId(),
                recording == null ? null : recording.getStatus(),
                recording == null ? null : recording.getEndReason(),
                recording == null ? null : recording.getRecorder());
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
