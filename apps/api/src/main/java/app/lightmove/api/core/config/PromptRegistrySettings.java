package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/** The prompt registry's rollout switch — {@code lightmove.ai.prompt-registry.*}. */
public record PromptRegistrySettings(
        /** Off: every prompt is read from its bundled file, as before the registry. On: the production version serves. */
        @DefaultValue("false") boolean enabled
) {

    public static PromptRegistrySettings off() {
        return new PromptRegistrySettings(false);
    }
}
