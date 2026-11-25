package app.lightmove.api.core.llm.model;

import app.lightmove.api.core.llm.constant.PromptKind;

/**
 * What a calling feature fixes about its prompt: the registry id, the name its calls are logged and billed under,
 * how the answer is read, and the model and settings used when the serving version names none.
 * {@code defaultModel} null is the deployment's chat model.
 */
public record PromptCallSpec(
        String promptId,
        String callLabel,
        PromptKind kind,
        String defaultModel,
        GenerationSettings defaults
) {

    /** Extraction and mapping have one right answer, so variance buys only answers that will not bind. */
    private static final double STRUCTURED_TEMPERATURE = 0.0;

    /**
     * No reasoning step: reading a document onto a fixed field list is not a problem thinking
     * improves, and left at the default the model deliberated before every answer — billed output
     * tokens and seconds a person is waiting on.
     */
    private static final int STRUCTURED_THINKING_BUDGET = 0;

    public static PromptCallSpec structured(String promptId) {
        return structured(promptId, null);
    }

    /** The same, answered by {@code defaultModel} where the serving version names none. */
    public static PromptCallSpec structured(String promptId, String defaultModel) {
        return new PromptCallSpec(promptId, promptId, PromptKind.STRUCTURED, defaultModel,
                new GenerationSettings(STRUCTURED_TEMPERATURE, STRUCTURED_THINKING_BUDGET, null));
    }

    public static PromptCallSpec prose(String promptId) {
        return new PromptCallSpec(promptId, promptId, PromptKind.PROSE, null, GenerationSettings.UNSET);
    }

    public static PromptCallSpec toolCalling(String promptId, String callLabel, String defaultModel,
                                             GenerationSettings defaults) {
        return new PromptCallSpec(promptId, callLabel, PromptKind.TOOL_CALLING, defaultModel, defaults);
    }
}
