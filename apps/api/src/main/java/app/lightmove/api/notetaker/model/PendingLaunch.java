package app.lightmove.api.notetaker.model;

import app.lightmove.api.notetaker.constant.NotetakerPlatform;
import java.time.Instant;
import java.util.UUID;

/** A claimed run whose bot is still to be sent, holding the only copy of its run token. */
public record PendingLaunch(UUID recordingId, NotetakerPlatform platform, String joinUrl, String runToken,
                            Instant endsAt, UUID workspaceId, UUID userId, UUID personId) {

    @Override
    public String toString() {
        return "PendingLaunch[recordingId=" + recordingId + ", platform=" + platform + ", runToken=<redacted>]";
    }
}
