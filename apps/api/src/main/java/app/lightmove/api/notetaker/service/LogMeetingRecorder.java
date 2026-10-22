package app.lightmove.api.notetaker.service;

import app.lightmove.api.notetaker.model.NotetakerLaunch;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/**
 * The recorder of a deployment that cannot start a bot yet (#951 brings the browser bot): it logs the launch,
 * and the run fails as never reported once its launch timeout passes.
 */
@Slf4j
public class LogMeetingRecorder implements MeetingRecorder {

    @Override
    public String launch(NotetakerLaunch launch) {
        log.info("Notetaker would join a call now: {}", launch);
        return "log-" + UUID.randomUUID();
    }

    @Override
    public void requestLeave(String externalId) {
        log.info("Notetaker {} would be asked to leave", externalId);
    }
}
