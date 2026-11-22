package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.convert.DurationStyle;

/** The prompt registry's rollout switch — {@code lightmove.ai.prompt-registry.*}. */
public record PromptRegistrySettings(
        /** Off: every prompt is read from its bundled file, as before the registry. On: the production version serves. */
        @DefaultValue("false") boolean enabled,

        /** How often an instance re-reads the platform configuration versions, in case it missed a notification. */
        @DefaultValue(PromptRegistrySettings.DEFAULT_RELOAD_CHECK) Duration reloadCheck
) {

    static final String DEFAULT_RELOAD_CHECK = "30s";

    public static PromptRegistrySettings off() {
        return new PromptRegistrySettings(false, DurationStyle.detectAndParse(DEFAULT_RELOAD_CHECK));
    }
}
