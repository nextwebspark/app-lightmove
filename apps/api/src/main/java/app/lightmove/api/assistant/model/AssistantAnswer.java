package app.lightmove.api.assistant.model;

/**
 * What a turn came back with, and what it cost.
 *
 * <p>The counts travel with the answer because the caller is the only place that can attribute them:
 * by the time a turn's spend reaches a per-workspace total, nothing else still knows which workspace
 * asked. Either may be null — a provider is not obliged to report usage, and a missing count must not
 * read as a free call.
 */
public record AssistantAnswer(String text, String model, Integer inputTokens, Integer outputTokens) {

    public AssistantAnswer {
        if (text == null) {
            throw new IllegalArgumentException("an answer must carry text, even if empty");
        }
    }
}
