package app.lightmove.api.assistant.model;

/** What one answer's model calls took, summed over the supervisor and every specialist it asked. */
public record ModelSpend(String model, int inputTokens, int outputTokens) {

    public static final ModelSpend NONE = new ModelSpend(null, 0, 0);

    public ModelSpend plus(String calledModel, Integer input, Integer output) {
        return new ModelSpend(model == null ? calledModel : model, inputTokens + orZero(input),
                outputTokens + orZero(output));
    }

    private static int orZero(Integer tokens) {
        return tokens == null ? 0 : tokens;
    }
}
