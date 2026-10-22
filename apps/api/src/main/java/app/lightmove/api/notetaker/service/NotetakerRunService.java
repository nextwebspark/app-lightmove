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
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A bot's calls back about its own run. The run token is the whole credential and names one run: a wrong token,
 * another run's, or one whose run has ended is answered as a run that does not exist.
 */
@Service
@RequiredArgsConstructor
public class NotetakerRunService {

    /** V152's CHECK: three hours of 30-second pieces is 360, so this is room, not a target. */
    static final int MAX_SEQUENCE = 100_000;

    private final MeetingRecordingRepository recordings;
    private final CandidateOutreachService people;
    private final RateLimitGuard rateLimit;
    private final MeetingMediaPieces pieces;
    private final DocumentStore store;
    private final LightMoveProperties properties;
    private final ApplicationEventPublisher events;
    private final NotetakerRunEndings endings;
    private final BotIdentityVerifier bots;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /**
     * A Cloud Run bot's first call (#956): its service account's ID token, verified against Google's keys before the
     * database is asked, buys its run's token once. Every refusal reads as a run that does not exist.
     */
    public String exchangeToken(UUID runId, String identityToken, HttpServletRequest from) {
        rateLimit.checkNotetakerRun(runId, from);
        String expected = properties.notetaker().botServiceAccount();
        if (expected == null || expected.isBlank() || identityToken == null || identityToken.isBlank()
                || bots.serviceAccountOf(identityToken.strip()).filter(expected.strip()::equalsIgnoreCase).isEmpty()) {
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }
        String runToken = Tokens.generate();
        boolean issued = Boolean.TRUE.equals(transactions.execute(status -> recordings.findById(runId)
                .filter(MeetingRecording::canIssueRunToken)
                .map(recording -> {
                    recording.issueRunToken(Tokens.hash(runToken), clock.instant());
                    return true;
                })
                .orElse(false)));
        if (!issued) {
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }
        return runToken;
    }

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
                endings.fail(recording, request.reason(), clock.instant());
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
    public void storeMedia(UUID runId, String runToken, MeetingMediaKind kind, int sequence, String startedAt,
                           InputStream content, long sizeBytes, HttpServletRequest from) {
        MeetingRecording recording = requireRun(runId, runToken, from);
        Instant began = kind == MeetingMediaKind.AUDIO ? startOf(startedAt) : null;
        if (sequence < 1 || sequence > MAX_SEQUENCE || sizeBytes <= 0
                || sizeBytes > properties.notetaker().maxMediaBytes()) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED);
        }
        if (recording.isToLeave()) {
            return;
        }
        // A removal landing while this piece is stored still wins: the row recorded below lists the file, and the
        // purge deletes every REMOVED run's listed pieces on its next pass.
        String key = kind.storageKeyOf(runId, sequence);
        try {
            store.put(key, content, sizeBytes, kind.contentType());
        } catch (IOException failed) {
            throw new IllegalStateException("Notetaker piece " + key + " could not be stored", failed);
        }
        pieces.record(runId, kind, sequence, key, sizeBytes, began);
    }

    @Transactional
    public void complete(UUID runId, String runToken, HttpServletRequest from) {
        MeetingRecording recording = requireRun(runId, runToken, from);
        recording.complete(clock.instant());
        if (recording.getStatus() == NotetakerStatus.PROCESSING) {
            events.publishEvent(new MeetingRecordingCompleted(recording.getWorkspaceId(), recording.getId()));
        }
    }

    /** When an audio piece began, by the bot's word; one that does not say is taken to have begun as it arrived. */
    private Instant startOf(String startedAt) {
        if (startedAt == null || startedAt.isBlank()) {
            return clock.instant();
        }
        try {
            return Instant.parse(startedAt.strip());
        } catch (DateTimeParseException unreadable) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED);
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
