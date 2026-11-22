package app.lightmove.api.promptregistry.dto;

import app.lightmove.api.core.llm.constant.AiProvider;
import app.lightmove.api.core.llm.model.GenerationSettings;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A new version: the text, the model (both of {@code provider} and {@code modelId}, or neither for the configured
 * model), the settings it fixes (null or a null field: the feature's default) and why.
 */
public record SavePromptVersionRequest(
        @NotBlank @Size(max = SavePromptVersionRequest.LONGEST_BODY) String body,
        AiProvider provider,
        @Size(max = 80) String modelId,
        GenerationSettings settings,
        @NotBlank @Size(max = 300) String changeNote
) {

    public static final int LONGEST_BODY = 40_000;
}
