package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.notetaker.constant.MeetingRecorderKind;
import app.lightmove.api.notetaker.model.BackupBotNeeded;
import app.lightmove.api.notetaker.model.LaunchedBot;
import app.lightmove.api.notetaker.model.MeetingRecording;
import app.lightmove.api.notetaker.model.NotetakerLaunch;
import app.lightmove.api.notetaker.model.PendingLaunch;
import app.lightmove.api.notetaker.repository.MeetingRecordingRepository;
import java.time.Clock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends the bot of a claimed run, outside any transaction: the recorder calls out to start a machine, or Recall's. The
 * chain decides whose; a run handed to the backup mid-call has its bot sent here too, once that handover commits.
 */
@Component
@Slf4j
class NotetakerLauncher {

    private final ChainedMeetingRecorder recorders;
    private final MeetingRecordingRepository recordings;
    private final NotetakerRunEndings endings;
    private final TransactionTemplate transactions;
    private final LightMoveProperties properties;
    private final Clock clock;

    /**
     * Every write in a transaction of its own: the backup's bot is sent after the handover committed, when that
     * transaction's resources are still bound and a joined write would be lost with it.
     */
    NotetakerLauncher(ChainedMeetingRecorder recorders, MeetingRecordingRepository recordings,
                      NotetakerRunEndings endings, PlatformTransactionManager transactionManager,
                      LightMoveProperties properties, Clock clock) {
        this.recorders = recorders;
        this.recordings = recordings;
        this.endings = endings;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.properties = properties;
        this.clock = clock;
    }

    void launch(PendingLaunch pending) {
        LaunchedBot launched;
        try {
            launched = recorders.launch(new NotetakerLaunch(pending.recordingId(), pending.platform(),
                    pending.joinUrl(), properties.notetaker().botName(), pending.runToken(), pending.endsAt(),
                    pending.workspaceId(), pending.userId(), pending.personId()));
        } catch (BotNotSent failed) {
            log.warn("Notetaker run {} could not be launched: {}", pending.recordingId(), failed.reason(),
                    failed.getCause());
            transactions.executeWithoutResult(status -> recordings.findById(pending.recordingId())
                    .ifPresent(recording -> endings.failWithoutBackup(recording, failed.reason(), clock.instant())));
            return;
        }
        transactions.executeWithoutResult(status -> recordings.findById(pending.recordingId())
                .ifPresent(recording -> marked(recording, launched)));
    }

    /** The backup's bot for a run ours gave up mid-way, sent once the handover is committed. */
    @TransactionalEventListener
    void onBackupNeeded(BackupBotNeeded needed) {
        MeetingRecording recording = transactions.execute(status ->
                recordings.findById(needed.recordingId()).orElse(null));
        if (recording == null || recording.getRecorder() != MeetingRecorderKind.RECALL
                || recording.getExternalId() != null) {
            return;
        }
        LaunchedBot launched;
        try {
            launched = recorders.launchBackup(new NotetakerLaunch(recording.getId(), recording.getPlatform(),
                    recording.getJoinUrl(), properties.notetaker().botName(), null, recording.getEndsAt(),
                    recording.getWorkspaceId(), recording.getUserId(), recording.getPersonId()),
                    recording.getFallbackReason());
        } catch (BotNotSent failed) {
            log.warn("Notetaker run {}: the backup could not be sent either", recording.getId(), failed.getCause());
            transactions.executeWithoutResult(status -> recordings.findById(recording.getId())
                    .filter(fresh -> fresh.getStatus().isInCall())
                    .ifPresent(fresh -> endings.failWithoutBackup(fresh, failed.reason(), clock.instant())));
            return;
        }
        transactions.executeWithoutResult(status -> recordings.findById(recording.getId())
                .ifPresent(fresh -> fresh.markLaunched(launched.externalId(), clock.instant())));
    }

    private void marked(MeetingRecording recording, LaunchedBot launched) {
        if (launched.recorder() == MeetingRecorderKind.RECALL) {
            recording.launchedAsBackup(launched.externalId(), launched.fallbackReason(), clock.instant());
        } else {
            recording.markLaunched(launched.externalId(), clock.instant());
        }
    }
}
