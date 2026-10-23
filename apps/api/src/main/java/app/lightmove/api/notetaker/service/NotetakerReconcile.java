package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.notetaker.constant.NotetakerStatus;
import app.lightmove.api.notetaker.model.MeetingRecording;
import app.lightmove.api.notetaker.model.RecallBotNotice;
import app.lightmove.api.notetaker.model.RecallBotState;
import app.lightmove.api.notetaker.repository.MeetingRecordingRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The safety net under Recall's webhook: a recording still live well after its call ended is read from Recall and
 * folded on as the delivery would have been. One left live a day after its call is given up on.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotetakerReconcile {

    /** Recall's transcript lands minutes after the call; past this a missing delivery is the likelier story. */
    static final Duration QUIET_AFTER_END = Duration.ofMinutes(30);
    static final Duration GIVE_UP_AFTER_END = Duration.ofHours(24);
    static final int BATCH = 20;

    private final RecallBotApi recall;
    private final MeetingRecordingRepository recordings;
    private final NotetakerWebhook webhook;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${lightmove.recall.bot-reconcile-interval}",
            initialDelayString = "${lightmove.recall.bot-reconcile-interval}")
    public void scheduledReconcile() {
        if (recall.isOffered()) {
            reconcileAt(clock.instant());
        }
    }

    public int reconcileAt(Instant now) {
        List<MeetingRecording> stale = transactions.execute(status -> recordings
                .findByStatusInAndEndsAtBeforeAndRecallBotIdIsNotNullOrderByEndsAtAsc(NotetakerStatus.LIVE,
                        now.minus(QUIET_AFTER_END), Limit.of(BATCH)));
        int moved = 0;
        for (MeetingRecording recording : stale) {
            RecallBotState state;
            try {
                state = recall.state(recording.getRecallBotId());
            } catch (VendorException unreadable) {
                log.warn("Notetaker recording {}: Recall could not be read: {}", recording.getId(),
                        unreadable.getMessage());
                continue;
            }
            if (fold(recording.getId(), state, now)) {
                moved++;
            }
        }
        return moved;
    }

    private boolean fold(UUID recordingId, RecallBotState state, Instant now) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            MeetingRecording recording = recordings.findById(recordingId).orElse(null);
            if (recording == null || !recording.getStatus().isLive()) {
                return false;
            }
            NotetakerStatus before = recording.getStatus();
            String transcriptBefore = recording.getTranscriptId();
            // The transcript first: a bot that brought one back recorded, whatever its last status says.
            if (state.transcriptId() != null) {
                recording.heard(state.statusCode(), state.subCode(), NotetakerStatus.RECORDING);
                // A transcript already handed over is handed again only once nothing has touched the row for a while:
                // the worker that took it died with its instance.
                boolean handedOver = recording.getTranscriptId() != null
                        && recording.getUpdatedAt().isAfter(now.minus(QUIET_AFTER_END));
                if ("done".equals(state.transcriptStatus()) && !handedOver) {
                    webhook.apply(recording, new RecallBotNotice.TranscriptReady(recording.getRecallBotId(),
                            state.transcriptId()));
                } else if ("failed".equals(state.transcriptStatus())) {
                    webhook.apply(recording, new RecallBotNotice.TranscriptFailed(recording.getRecallBotId()));
                }
            }
            if (recording.getStatus().isLive() && state.statusCode() != null) {
                webhook.apply(recording, new RecallBotNotice.StatusChanged(recording.getRecallBotId(),
                        state.statusCode(), state.subCode(), state.statusAt()));
            }
            if (recording.getStatus().isLive() && recording.getEndsAt().plus(GIVE_UP_AFTER_END).isBefore(now)) {
                recording.failed("TIMED_OUT");
            }
            return recording.getStatus() != before || !Objects.equals(transcriptBefore,
                    recording.getTranscriptId());
        }));
    }
}
