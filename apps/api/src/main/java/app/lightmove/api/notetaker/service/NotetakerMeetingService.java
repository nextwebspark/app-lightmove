package app.lightmove.api.notetaker.service;

import app.lightmove.api.candidate.model.OutreachRecipient;
import app.lightmove.api.candidate.service.CandidateOutreachService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.notetaker.constant.NotetakerPlatform;
import app.lightmove.api.notetaker.dto.NotetakerMeetingResponse;
import app.lightmove.api.notetaker.dto.NotetakerMeetingsResponse;
import app.lightmove.api.notetaker.model.MeetingRecording;
import app.lightmove.api.notetaker.repository.MeetingRecordingRepository;
import app.lightmove.api.outreach.model.KeptMeeting;
import app.lightmove.api.outreach.service.KeptMeetings;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
 * to, and Remove notetaker. A switch applies to the call, so a teammate's copy of it on their calendar follows.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotetakerMeetingService {

    private final KeptMeetings meetings;
    private final CandidateOutreachService people;
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
        Set<NotetakerPlatform> offered = NotetakerScheduler.offeredPlatforms(properties.notetaker());
        List<String> callKeys = rows.stream()
                .filter(row -> row.joinUrl() != null)
                .map(NotetakerMeetingService::callKeyOf)
                .distinct()
                .toList();
        Map<String, MeetingRecording> recordingsByCall = callKeys.isEmpty() ? Map.of()
                : recordings.findByWorkspaceIdAndCallKeyIn(workspaceId, callKeys).stream()
                        .collect(Collectors.toMap(MeetingRecording::getCallKey, Function.identity()));
        Set<String> optedOut = switches.optedOutAmong(rows);
        List<NotetakerMeetingResponse> responses = rows.stream()
                .map(row -> responseOf(row, offered, optedOut, recordingsByCall))
                .toList();
        return new NotetakerMeetingsResponse(properties.notetaker().enabled(), switches.workspaceAllows(workspaceId),
                switches.userAllows(userId, workspaceId), responses);
    }

    public void setEnabled(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId, UUID meetingId,
                           boolean enabled, HttpServletRequest httpRequest) {
        requireOffered();
        KeptMeeting meeting = requireMeeting(workspaceId, projectId, candidateId, meetingId);
        if (!meeting.startsAt().isAfter(clock.instant())) {
            throw ApiException.of(ErrorCode.NOTETAKER_MEETING_STARTED);
        }
        List<KeptMeeting> sameCall = meetings.sameCallAs(meeting);
        transactions.executeWithoutResult(status -> {
            switches.setMeeting(sameCall, enabled, userId);
            audit.projectEvent(ProjectEventType.NOTETAKER_MEETING_SWITCHED, userId, workspaceId, projectId,
                            httpRequest)
                    .detail("candidateId", candidateId.toString())
                    .detail("meetingId", meetingId.toString())
                    .detail("enabled", String.valueOf(enabled))
                    .record();
        });
    }

    /** The bot hears it on its next heartbeat; Recall's is told at once, once the request is on file. */
    public void remove(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId, UUID meetingId,
                       HttpServletRequest httpRequest) {
        requireOffered();
        KeptMeeting meeting = requireMeeting(workspaceId, projectId, candidateId, meetingId);
        Instant now = clock.instant();
        Optional<String> told = transactions.execute(status -> {
            MeetingRecording recording = Optional.ofNullable(meeting.joinUrl())
                    .flatMap(joinUrl -> recordings.findByWorkspaceIdAndCallKey(workspaceId, callKeyOf(meeting)))
                    .filter(found -> found.getStatus().isInCall())
                    .orElseThrow(() -> ApiException.of(ErrorCode.NOTETAKER_NOT_IN_CALL));
            recording.requestRemoval(userId, now);
            audit.projectEvent(ProjectEventType.NOTETAKER_REMOVED, userId, workspaceId, projectId, httpRequest)
                    .detail("candidateId", candidateId.toString())
                    .detail("recordingId", recording.getId().toString())
                    .record();
            return Optional.ofNullable(recording.getExternalId());
        });
        told.ifPresent(externalId -> {
            try {
                recorder.requestLeave(externalId);
            } catch (RuntimeException failed) {
                log.warn("Notetaker {} could not be told to leave; its heartbeat will", externalId, failed);
            }
        });
    }

    private NotetakerMeetingResponse responseOf(KeptMeeting row, Set<NotetakerPlatform> offered, Set<String> optedOut,
                                                Map<String, MeetingRecording> recordingsByCall) {
        Optional<NotetakerPlatform> platform = row.joinUrl() == null ? Optional.empty()
                : NotetakerPlatform.of(row.joinUrl(), row.conferencingProvider());
        MeetingRecording recording = row.joinUrl() == null ? null : recordingsByCall.get(callKeyOf(row));
        return new NotetakerMeetingResponse(row.id(), platform.orElse(null),
                platform.filter(offered::contains).isPresent(),
                !optedOut.contains(NotetakerSwitches.eventKeyOf(row)),
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

    private static String callKeyOf(KeptMeeting meeting) {
        return NotetakerCallKeys.of(meeting.joinUrl(), meeting.startsAt());
    }
}
