package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.notetaker.constant.NotetakerEndReason;
import app.lightmove.api.notetaker.model.NotetakerLaunch;
import app.lightmove.api.notetaker.model.PendingLaunch;
import app.lightmove.api.notetaker.repository.MeetingRecordingRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Sends the bot of a claimed run, outside any transaction: the recorder calls out to start a machine. */
@Component
@RequiredArgsConstructor
@Slf4j
class NotetakerLauncher {

    private final MeetingRecorder recorder;
    private final MeetingRecordingRepository recordings;
    private final TransactionTemplate transactions;
    private final LightMoveProperties properties;
    private final Clock clock;

    void launch(PendingLaunch pending) {
        String externalId;
        try {
            externalId = recorder.launch(new NotetakerLaunch(pending.recordingId(), pending.platform(),
                    pending.joinUrl(), properties.notetaker().botName(), pending.runToken(), pending.endsAt()));
        } catch (RuntimeException failed) {
            log.warn("Notetaker run {} could not be launched", pending.recordingId(), failed);
            transactions.executeWithoutResult(status -> recordings.findById(pending.recordingId())
                    .ifPresent(recording -> recording.fail(NotetakerEndReason.LAUNCH_FAILED, clock.instant())));
            return;
        }
        transactions.executeWithoutResult(status -> recordings.findById(pending.recordingId())
                .ifPresent(recording -> recording.markLaunched(externalId, clock.instant())));
    }
}
