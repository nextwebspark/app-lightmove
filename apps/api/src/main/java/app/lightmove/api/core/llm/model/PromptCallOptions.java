package app.lightmove.api.core.llm.model;

import app.lightmove.api.core.llm.constant.PromptKind;

/** What a provider adapter turns into its own options. {@code model} null: the deployment's chat model. */
public record PromptCallOptions(
        String callLabel,
        Integer promptVersion,
        PromptKind kind,
        String model,
        GenerationSettings settings
) {
}
