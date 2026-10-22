package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.token.Tokens;
import app.lightmove.api.notetaker.constant.NotetakerEndReason;
import app.lightmove.api.notetaker.constant.NotetakerStatus;
import app.lightmove.api.notetaker.dto.NotetakerHeartbeatResponse;
import app.lightmove.api.notetaker.dto.NotetakerRunCompleteRequest;
import app.lightmove.api.notetaker.dto.NotetakerRunReportRequest;
import app.lightmove.api.notetaker.model.MeetingRecording;
import app.lightmove.api.notetaker.model.MeetingRecordingCompleted;
import app.lightmove.api.notetaker.model.RecordedMedia;
import app.lightmove.api.notetaker.repository.MeetingRecordingRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A bot's calls back about its own run. The run token is the whole credential and names one run: a wrong token,
 * another run's, or one whose run has ended is answered as a run that does not exist.
 */
@Service
@RequiredArgsConstructor
public class NotetakerRunService {

    /** A bot writes only under its own run's prefix, so it cannot hand us another run's files to read. */
    static final String MEDIA_PREFIX = "notetaker/";

    private final MeetingRecordingRepository recordings;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public void report(UUID runId, String runToken, NotetakerRunReportRequest request) {
        MeetingRecording recording = requireRun(runId, runToken);
        switch (request.state()) {
            case WAITING_ROOM -> recording.reportWaiting(clock.instant());
            case RECORDING -> recording.reportRecording(clock.instant());
            case FAILED -> {
                if (request.reason() == null || !NotetakerEndReason.REPORTED_BY_BOT.contains(request.reason())) {
                    throw ApiException.of(ErrorCode.VALIDATION_FAILED);
                }
                recording.fail(request.reason(), clock.instant());
            }
        }
    }

    @Transactional
    public NotetakerHeartbeatResponse heartbeat(UUID runId, String runToken) {
        MeetingRecording recording = requireRun(runId, runToken);
        recording.heartbeat(clock.instant());
        return new NotetakerHeartbeatResponse(recording.isToLeave());
    }

    @Transactional
    public void complete(UUID runId, String runToken, NotetakerRunCompleteRequest request) {
        MeetingRecording recording = requireRun(runId, runToken);
        String ownPrefix = MEDIA_PREFIX + runId + "/";
        List<String> audioKeys = request.audioKeys() == null ? List.of() : request.audioKeys();
        boolean outsideOwnPrefix = audioKeys.stream().anyMatch(key -> !key.startsWith(ownPrefix))
                || (request.captionsKey() != null && !request.captionsKey().startsWith(ownPrefix));
        if (outsideOwnPrefix) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED);
        }
        recording.complete(new RecordedMedia(audioKeys, request.captionsKey()), clock.instant());
        if (recording.getStatus() == NotetakerStatus.PROCESSING) {
            events.publishEvent(new MeetingRecordingCompleted(recording.getWorkspaceId(), recording.getId()));
        }
    }

    private MeetingRecording requireRun(UUID runId, String runToken) {
        if (runToken == null || runToken.isBlank()) {
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }
        byte[] presented = Tokens.hash(runToken).getBytes(StandardCharsets.US_ASCII);
        return recordings.findById(runId)
                .filter(recording -> recording.getRunTokenHash() != null && MessageDigest.isEqual(presented,
                        recording.getRunTokenHash().getBytes(StandardCharsets.US_ASCII)))
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }
}
