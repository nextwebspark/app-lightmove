package app.lightmove.api.notetaker.model;

import app.lightmove.api.notetaker.constant.NotetakerPlatform;
import java.time.Instant;
import java.util.UUID;

/**
 * What a recorder needs to send a bot. The run token is the bot's only credential, so it never reaches a log; a backup
 * bot, which reports by webhook, is sent without one. The workspace, consultant and person tag a backup bot, and the
 * consultant's own Zoom account is what lets one into a Zoom call.
 */
public record NotetakerLaunch(UUID recordingId, NotetakerPlatform platform, String joinUrl, String botName,
                              String runToken, Instant endsAt, UUID workspaceId, UUID userId, UUID personId) {

    @Override
    public String toString() {
        return "NotetakerLaunch[recordingId=" + recordingId + ", platform=" + platform + ", botName=" + botName
                + ", runToken=<redacted>, endsAt=" + endsAt + "]";
    }
}
