package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.NotetakerSettings;
import app.lightmove.api.notetaker.constant.NotetakerEndReason;
import app.lightmove.api.notetaker.constant.NotetakerStatus;
import app.lightmove.api.notetaker.model.MeetingRecording;
import app.lightmove.api.notetaker.repository.MeetingRecordingRepository;
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ends the runs whose bot is gone: one that never reported after being sent, one that fell silent, and a backup bot
 * never heard from again. Each run
 * is closed in its own transaction and read again inside it, so a heartbeat landing mid-sweep keeps its run and
 * one clash never rolls back the rest.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class NotetakerSweeper {

    private final MeetingRecordingRepository recordings;
    private final TransactionTemplate transactions;
    private final LightMoveProperties properties;
    private final NotetakerRunEndings endings;
    private final NotetakerBackups backups;

    void endStale(Instant now) {
        NotetakerSettings settings = properties.notetaker();
        Instant sentBefore = now.minus(settings.launchTimeout());
        Instant heardBefore = now.minus(settings.heartbeatTimeout());
        recordings.findNeverReported(sentBefore).forEach(recording -> end(recording.getId(), now,
                NotetakerEndReason.LAUNCH_TIMEOUT,
                fresh -> fresh.getLastHeartbeatAt() == null && fresh.getStatus().isInCall()));
        recordings.findSilentSince(EnumSet.of(NotetakerStatus.WAITING_ROOM, NotetakerStatus.RECORDING,
                        NotetakerStatus.REMOVE_REQUESTED), heardBefore)
                .forEach(recording -> end(recording.getId(), now, NotetakerEndReason.HEARTBEAT_LOST,
                        fresh -> fresh.getStatus().isInCall() && fresh.getLastHeartbeatAt() != null
                                && fresh.getLastHeartbeatAt().isBefore(heardBefore)));
        Instant endedBefore = now.minus(settings.backupTimeout());
        recordings.findBackupsLostSince(endedBefore).forEach(recording -> endLostBackup(recording.getId(), now,
                endedBefore));
    }

    /**
     * A backup bot never heard from again: its run ends, and once that commits Recall is told to drop the bot and what
     * it kept, since its webhook — the usual place that is done — never came. No transcript is waited for.
     */
    private void endLostBackup(UUID recordingId, Instant now, Instant endedBefore) {
        String botId;
        try {
            botId = transactions.execute(status -> recordings.findById(recordingId)
                    .filter(fresh -> fresh.getStatus().isInCall() && fresh.getEndsAt().isBefore(endedBefore))
                    .map(recording -> {
                        String externalId = recording.getExternalId();
                        recording.backupTranscribed(now);
                        endings.fail(recording, NotetakerEndReason.BACKUP_FAILED, now);
                        return externalId;
                    })
                    .orElse(null));
        } catch (ObjectOptimisticLockingFailureException moved) {
            log.debug("Notetaker run {} moved while it was being swept; left for the next pass", recordingId);
            return;
        }
        if (botId != null) {
            backups.forget(botId);
        }
    }

    private void end(UUID recordingId, Instant now, NotetakerEndReason reason, Predicate<MeetingRecording> stillStale) {
        try {
            transactions.executeWithoutResult(status -> recordings.findById(recordingId)
                    .filter(stillStale)
                    .ifPresent(recording -> endings.fail(recording, reason, now)));
        } catch (ObjectOptimisticLockingFailureException moved) {
            log.debug("Notetaker run {} moved while it was being swept; left for the next pass", recordingId);
        }
    }
}
