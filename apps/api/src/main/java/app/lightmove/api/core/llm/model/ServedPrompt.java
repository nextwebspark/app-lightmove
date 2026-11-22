package app.lightmove.api.core.llm.model;

import app.lightmove.api.core.llm.constant.AiProvider;

/**
 * The text a prompt is answered with now. {@code version} is null when it came straight from the bundled file (the
 * registry off or unreadable), and {@code modelId} is null when the deployment's configured model answers.
 */
public record ServedPrompt(
        String promptId,
        String body,
        Integer version,
        AiProvider provider,
        String modelId,
        GenerationSettings settings
) {

    public static ServedPrompt bundled(String promptId, String body) {
        return new ServedPrompt(promptId, body, null, null, null, GenerationSettings.UNSET);
    }

    public ServedPrompt onTheConfiguredModel() {
        return new ServedPrompt(promptId, body, version, null, null, settings);
    }
}
