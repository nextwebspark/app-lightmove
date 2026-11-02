package app.lightmove.api.core.platformconfig.model;

import app.lightmove.api.core.platformconfig.constant.PlatformPrompt;
import app.lightmove.api.core.platformconfig.constant.PlatformSetting;
import java.time.Instant;
import java.util.Map;

/** The overrides one instance runs on, loaded whole: typed values by setting, active bodies by prompt. */
public record PlatformConfigSnapshot(
        long version,
        Map<PlatformSetting, Object> settings,
        Map<PlatformPrompt, String> prompts,
        Instant loadedAt
) {

    public PlatformConfigSnapshot {
        settings = Map.copyOf(settings);
        prompts = Map.copyOf(prompts);
    }

    /** Before the first load: every value the deployment's own, every prompt the shipped file. */
    public static PlatformConfigSnapshot unloaded() {
        return new PlatformConfigSnapshot(-1, Map.of(), Map.of(), null);
    }

    public boolean isLoaded() {
        return loadedAt != null;
    }
}
