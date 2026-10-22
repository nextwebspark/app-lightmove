package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.config.NotetakerSettings;
import app.lightmove.api.notetaker.constant.NotetakerEndReason;
import java.util.EnumSet;
import java.util.Set;

/**
 * Which failures of our own bot Recall's follows: only our software's — not starting, breaking, falling silent. A bot
 * nobody admitted, or one the host or tenant lets no guest in for, was kept out by someone's decision, so neither is
 * followed unless the deployment opts in. Nothing after the call is a recorder's to retry, and neither is anything
 * else a person decided (do not contact, the consent line the chat would not take).
 */
public record FallbackPolicy(boolean enabled, boolean onNotAdmitted, boolean onJoinBlocked) {

    private static final Set<NotetakerEndReason> OURS_TO_COVER = EnumSet.of(NotetakerEndReason.LAUNCH_FAILED,
            NotetakerEndReason.LAUNCH_TIMEOUT, NotetakerEndReason.CRASHED, NotetakerEndReason.HEARTBEAT_LOST);

    public static FallbackPolicy of(NotetakerSettings settings) {
        return new FallbackPolicy(settings.fallback(), settings.fallbackOnNotAdmitted(),
                settings.fallbackOnJoinBlocked());
    }

    public boolean fallsBack(NotetakerEndReason reason) {
        if (!enabled || reason == null) {
            return false;
        }
        return OURS_TO_COVER.contains(reason) || (onNotAdmitted && reason == NotetakerEndReason.NOT_ADMITTED)
                || (onJoinBlocked && reason == NotetakerEndReason.JOIN_BLOCKED);
    }
}
