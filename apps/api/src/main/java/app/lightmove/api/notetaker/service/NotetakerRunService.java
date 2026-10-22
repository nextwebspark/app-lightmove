package app.lightmove.api.notetaker.service;

import app.lightmove.api.candidate.service.CandidateOutreachService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.ratelimit.service.RateLimitGuard;
import app.lightmove.api.core.security.token.Tokens;
import app.lightmove.api.core.storage.service.DocumentStore;
import app.lightmove.api.notetaker.constant.MeetingMediaKind;
import app.lightmove.api.notetaker.constant.NotetakerEndReason;
import app.lightmove.api.notetaker.constant.NotetakerStatus;
import app.lightmove.api.notetaker.dto.NotetakerHeartbeatResponse;
import app.lightmove.api.notetaker.dto.NotetakerRunReportRequest;
import app.lightmove.api.notetaker.model.MeetingRecording;
import app.lightmove.api.notetaker.model.MeetingRecordingCompleted;
import app.lightmove.api.notetaker.repository.MeetingRecordingRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
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

    /** V151's CHECK: three hours of 30-second pieces is 360, so this is room, not a target. */
    static final int MAX_SEQUENCE = 100_000;

    private final MeetingRecordingRepository recordings;
    private final CandidateOutreachService people;
    private final RateLimitGuard rateLimit;
    private final MeetingMediaPieces pieces;
    private final DocumentStore store;
    private final LightMoveProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public void report(UUID runId, String runToken, NotetakerRunReportRequest request, HttpServletRequest from) {
        MeetingRecording recording = requireRun(runId, runToken, from);
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
    public NotetakerHeartbeatResponse heartbeat(UUID runId, String runToken, HttpServletRequest from) {
        MeetingRecording recording = requireRun(runId, runToken, from);
        Instant now = clock.instant();
        recording.heartbeat(now);
        // Marked do not contact after the bot went in: it leaves as if removed, and keeps nothing.
        if (!recording.isToLeave() && recording.getPersonId() != null && !people.doNotContactAmong(
                recording.getWorkspaceId(), List.of(recording.getPersonId())).isEmpty()) {
            recording.requestRemoval(null, now);
        }
        return new NotetakerHeartbeatResponse(recording.isToLeave());
    }

    /**
     * One 30-second piece, stored under a key the server names. Outside any transaction: the bytes go to the bucket,
     * and a database connection must not wait on them. A piece from a bot asked to leave is dropped unread, since
     * nothing of a removed call is kept.
     */
    public void storeMedia(UUID runId, String runToken, MeetingMediaKind kind, int sequence, InputStream content,
                           long sizeBytes, HttpServletRequest from) {
        MeetingRecording recording = requireRun(runId, runToken, from);
        if (sequence < 1 || sequence > MAX_SEQUENCE || sizeBytes <= 0
                || sizeBytes > properties.notetaker().maxMediaBytes()) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED);
        }
        if (recording.isToLeave()) {
            return;
        }
        String key = kind.storageKeyOf(runId, sequence);
        try {
            store.put(key, content, sizeBytes, kind.contentType());
        } catch (IOException failed) {
            throw new IllegalStateException("Notetaker piece " + key + " could not be stored", failed);
        }
        pieces.record(runId, kind, sequence, key, sizeBytes);
    }

    @Transactional
    public void complete(UUID runId, String runToken, HttpServletRequest from) {
        MeetingRecording recording = requireRun(runId, runToken, from);
        recording.complete(clock.instant());
        if (recording.getStatus() == NotetakerStatus.PROCESSING) {
            events.publishEvent(new MeetingRecordingCompleted(recording.getWorkspaceId(), recording.getId()));
        }
    }

    private MeetingRecording requireRun(UUID runId, String runToken, HttpServletRequest from) {
        rateLimit.checkNotetakerRun(runId, from);
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
