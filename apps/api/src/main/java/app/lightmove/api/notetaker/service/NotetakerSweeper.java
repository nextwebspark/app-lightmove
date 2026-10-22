package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.NotetakerSettings;
import app.lightmove.api.notetaker.constant.NotetakerEndReason;
import app.lightmove.api.notetaker.constant.NotetakerStatus;
import app.lightmove.api.notetaker.repository.MeetingRecordingRepository;
import java.time.Instant;
import java.util.EnumSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Ends the runs whose bot is gone: one that never reported after being sent, and one that fell silent. */
@Component
@RequiredArgsConstructor
public class NotetakerSweeper {

    private final MeetingRecordingRepository recordings;
    private final LightMoveProperties properties;

    @Transactional
    public void failStale(Instant now) {
        NotetakerSettings settings = properties.notetaker();
        recordings.findNeverReported(now.minus(settings.launchTimeout()))
                .forEach(recording -> recording.fail(NotetakerEndReason.LAUNCH_TIMEOUT, now));
        recordings.findSilentSince(EnumSet.of(NotetakerStatus.WAITING_ROOM, NotetakerStatus.RECORDING,
                        NotetakerStatus.REMOVE_REQUESTED), now.minus(settings.heartbeatTimeout()))
                .forEach(recording -> recording.fail(NotetakerEndReason.HEARTBEAT_LOST, now));
    }
}
