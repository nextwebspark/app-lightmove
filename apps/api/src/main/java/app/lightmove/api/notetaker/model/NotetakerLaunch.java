package app.lightmove.api.notetaker.model;

import app.lightmove.api.notetaker.constant.NotetakerPlatform;
import java.time.Instant;
import java.util.UUID;

/** What a recorder needs to send a bot. The run token is the bot's only credential, so it never reaches a log. */
public record NotetakerLaunch(UUID recordingId, NotetakerPlatform platform, String joinUrl, String botName,
                              String runToken, Instant endsAt) {

    @Override
    public String toString() {
        return "NotetakerLaunch[recordingId=" + recordingId + ", platform=" + platform + ", botName=" + botName
                + ", runToken=<redacted>, endsAt=" + endsAt + "]";
    }
}
