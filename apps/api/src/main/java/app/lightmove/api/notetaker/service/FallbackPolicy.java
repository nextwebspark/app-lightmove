package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.config.NotetakerSettings;
import app.lightmove.api.notetaker.constant.NotetakerEndReason;
import java.util.EnumSet;
import java.util.Set;

/**
 * Which failures of our own bot Recall's follows. Ours not starting, being refused at the door, breaking or falling
 * silent are ours to cover; a bot nobody admitted is not, by default, since the host may have chosen to keep it out.
 * Nothing after the call — a note that would not write — is a recorder's to retry, and neither is anything a person
 * decided (do not contact, the consent line the chat would not take).
 */
public record FallbackPolicy(boolean enabled, boolean onNotAdmitted) {

    private static final Set<NotetakerEndReason> OURS_TO_COVER = EnumSet.of(NotetakerEndReason.LAUNCH_FAILED,
            NotetakerEndReason.LAUNCH_TIMEOUT, NotetakerEndReason.JOIN_BLOCKED, NotetakerEndReason.CRASHED,
            NotetakerEndReason.HEARTBEAT_LOST);

    public static FallbackPolicy of(NotetakerSettings settings) {
        return new FallbackPolicy(settings.fallback(), settings.fallbackOnNotAdmitted());
    }

    public boolean fallsBack(NotetakerEndReason reason) {
        if (!enabled || reason == null) {
            return false;
        }
        return OURS_TO_COVER.contains(reason) || (onNotAdmitted && reason == NotetakerEndReason.NOT_ADMITTED);
    }
}
