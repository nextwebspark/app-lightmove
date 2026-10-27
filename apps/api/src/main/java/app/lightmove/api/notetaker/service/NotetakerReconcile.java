package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.notetaker.constant.NotetakerStatus;
import app.lightmove.api.notetaker.model.FinalTranscriptWanted;
import app.lightmove.api.notetaker.model.MeetingRecording;
import app.lightmove.api.notetaker.model.MeetingTranscriptReady;
import app.lightmove.api.notetaker.model.RecallBotNotice;
import app.lightmove.api.notetaker.model.RecallBotState;
import app.lightmove.api.notetaker.repository.MeetingRecordingRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The safety net under Recall's webhook: a recording still live well after its call ended is read from Recall and
 * folded on as the delivery would have been. Rows are claimed before Recall is asked, so of several instances only one
 * reads each bot per pass; one left live a day after its call is given up on.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotetakerReconcile {

    /** Recall's transcript lands minutes after the call; past this a missing delivery is the likelier story. */
    static final Duration QUIET_AFTER_END = Duration.ofMinutes(30);
    /** A bot asked to leave early has no booked end to wait for: its call is over once it was told to go. */
    static final Duration QUIET_AFTER_END_REQUESTED = Duration.ofMinutes(5);
    /** A row read back once is not read again sooner than this, by this instance or another. */
    static final Duration READ_AGAIN_AFTER = Duration.ofMinutes(5);
    static final Duration GIVE_UP_AFTER_END = Duration.ofHours(24);
    static final int BATCH = 20;

    private static final String CLAIM = """
            UPDATE app_lm_meeting_recording SET reconciled_at = :now
            WHERE id IN (
                SELECT id FROM app_lm_meeting_recording
                WHERE status IN ('SCHEDULED', 'JOINING', 'RECORDING', 'PROCESSING') AND recall_bot_id IS NOT NULL
                  AND (ends_at < :endedBefore OR end_requested_at < :endRequestedBefore)
                  AND (reconciled_at IS NULL OR reconciled_at < :readBefore)
                ORDER BY coalesce(end_requested_at, ends_at)
                LIMIT :batch
                FOR UPDATE SKIP LOCKED)
            RETURNING id
            """;

    private final RecallBotApi recall;
    private final MeetingRecordingRepository recordings;
    private final NotetakerWebhook webhook;
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${lightmove.recall.bot-reconcile-interval}",
            initialDelayString = "${lightmove.recall.bot-reconcile-interval}")
    public void scheduledReconcile() {
        if (recall.isOffered()) {
            reconcileAt(clock.instant());
        }
    }

    public int reconcileAt(Instant now) {
        List<UUID> claimed = transactions.execute(status -> jdbc.queryForList(CLAIM, new MapSqlParameterSource()
                .addValue("now", Timestamp.from(now))
                .addValue("endedBefore", Timestamp.from(now.minus(QUIET_AFTER_END)))
                .addValue("endRequestedBefore", Timestamp.from(now.minus(QUIET_AFTER_END_REQUESTED)))
                .addValue("readBefore", Timestamp.from(now.minus(READ_AGAIN_AFTER)))
                .addValue("batch", BATCH), UUID.class));
        int moved = 0;
        for (UUID recordingId : claimed == null ? List.<UUID>of() : claimed) {
            MeetingRecording held = transactions.execute(status -> recordings.findById(recordingId).orElse(null));
            if (held == null) {
                continue;
            }
            RecallBotState state;
            String finalStatus;
            try {
                state = recall.state(held.getRecallBotId());
                finalStatus = held.getFinalTranscriptId() == null ? null
                        : recall.transcriptStatus(held.getFinalTranscriptId());
            } catch (VendorException unreadable) {
                log.warn("Notetaker recording {}: Recall could not be read: {}", recordingId, unreadable.getMessage());
                continue;
            }
            if (fold(recordingId, state, finalStatus, now)) {
                moved++;
            }
        }
        return moved;
    }

    private boolean fold(UUID recordingId, RecallBotState state, String finalStatus, Instant now) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            MeetingRecording recording = recordings.findById(recordingId).orElse(null);
            if (recording == null || !recording.getStatus().isLive()) {
                return false;
            }
            NotetakerStatus before = recording.getStatus();
            if (recording.isLiveCopilot()) {
                foldLive(recording, state, finalStatus);
            } else if (state.transcriptId() != null) {
                // The transcript first: a bot that brought one back recorded, whatever its last status says.
                recording.heard(state.statusCode(), state.subCode(), NotetakerStatus.RECORDING);
                if ("done".equals(state.transcriptStatus())) {
                    if (state.transcriptId().equals(recording.getTranscriptId())) {
                        // Handed over already, and still not filed: the worker that took it died with its instance.
                        // Its claim lapses after a while, so a worker still busy on it is never doubled.
                        events.publishEvent(new MeetingTranscriptReady(recordingId));
                    } else {
                        webhook.apply(recording, new RecallBotNotice.TranscriptReady(recording.getRecallBotId(),
                                state.transcriptId(), state.recordingId()));
                    }
                } else if ("failed".equals(state.transcriptStatus())) {
                    webhook.apply(recording, new RecallBotNotice.TranscriptFailed(recording.getRecallBotId(),
                            state.transcriptId(), state.recordingId()));
                }
            }
            if (recording.getStatus().isLive() && state.statusCode() != null) {
                webhook.apply(recording, new RecallBotNotice.StatusChanged(recording.getRecallBotId(),
                        state.statusCode(), state.subCode(), state.statusAt()));
            }
            if (recording.getStatus().isLive() && recording.getEndsAt().plus(GIVE_UP_AFTER_END).isBefore(now)) {
                recording.failed("TIMED_OUT");
                webhook.endLiveIfOver(recording);
            }
            return recording.getStatus() != before;
        }));
    }

    /**
     * A live copilot bot's own transcript is the low-latency stream: once it exists the call is over and the accurate
     * one is asked for, or folded on once Recall has it.
     */
    private void foldLive(MeetingRecording recording, RecallBotState state, String finalStatus) {
        if (state.transcriptId() != null) {
            recording.heard(state.statusCode(), state.subCode(), NotetakerStatus.RECORDING);
        }
        String finalId = recording.getFinalTranscriptId();
        if (finalId == null) {
            if (state.transcriptId() != null || "done".equals(state.statusCode())) {
                events.publishEvent(new FinalTranscriptWanted(recording.getId(), state.recordingId()));
            }
        } else if ("done".equals(finalStatus)) {
            if (finalId.equals(recording.getTranscriptId())) {
                events.publishEvent(new MeetingTranscriptReady(recording.getId()));
            } else {
                webhook.apply(recording, new RecallBotNotice.TranscriptReady(recording.getRecallBotId(), finalId,
                        state.recordingId()));
            }
        } else if ("failed".equals(finalStatus)) {
            webhook.apply(recording, new RecallBotNotice.TranscriptFailed(recording.getRecallBotId(), finalId,
                    state.recordingId()));
        }
    }
}
