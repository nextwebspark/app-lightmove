package app.lightmove.api.core.llm.model;

/**
 * The generation settings a prompt version may carry. A null field is "not set here": the calling feature's own
 * default applies, so a version that sets nothing behaves as the code always has.
 */
public record GenerationSettings(Double temperature, Integer thinkingBudget, Integer maxOutputTokens) {

    public static final GenerationSettings UNSET = new GenerationSettings(null, null, null);

    /** These settings, with each unset one taken from {@code defaults}. */
    public GenerationSettings orElse(GenerationSettings defaults) {
        return new GenerationSettings(
                temperature != null ? temperature : defaults.temperature,
                thinkingBudget != null ? thinkingBudget : defaults.thinkingBudget,
                maxOutputTokens != null ? maxOutputTokens : defaults.maxOutputTokens);
    }
}
