package app.lightmove.api.core.config;

/** How the AI prompts are served — {@code lightmove.ai.*}. */
public record AiSettings(PromptRegistrySettings promptRegistry) {

    public AiSettings {
        promptRegistry = promptRegistry == null ? PromptRegistrySettings.off() : promptRegistry;
    }

    public static AiSettings defaults() {
        return new AiSettings(null);
    }
}
