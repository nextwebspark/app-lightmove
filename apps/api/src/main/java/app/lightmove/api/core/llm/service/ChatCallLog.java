package app.lightmove.api.core.llm.service;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * What a call to the model writes to the log: <b>metadata only, never prompt or response content</b>.
 *
 * <p>A job brief, a candidate profile and a spreadsheet of executives are all client and candidate
 * PII, and {@code SimpleLoggerAdvisor}'s default formatters dump both in full — which is why both are
 * replaced.
 */
public final class ChatCallLog {

    /**
     * Advisor-context key naming the feature behind a call. The client is shared, so without it every
     * line says only that <i>something</i> called the model.
     */
    public static final String PROMPT_ID_ATTRIBUTE = "lightmove.promptId";

    private ChatCallLog() {
    }

    /** One line for the outgoing call: which feature, which model, how creative it was allowed to be. */
    public static String describeRequest(ChatClientRequest request) {
        if (request == null) {
            return "chat request: null";
        }
        ChatOptions options = request.prompt().getOptions();
        return "chat request prompt=%s model=%s temperature=%s".formatted(
                promptIdOf(request),
                options == null ? "default" : options.getModel(),
                options == null ? "default" : options.getTemperature());
    }

    /** One line for the reply: what it cost, and whether the model actually finished. */
    public static String describeResponse(ChatResponse response) {
        if (response == null) {
            return "chat response: null";
        }
        ChatResponseMetadata metadata = response.getMetadata();
        // Spring AI substitutes an empty usage when a provider reports none, so an unreported count
        // arrives as 0 rather than null: totalling spend from these lines reads a zero as free.
        Usage usage = metadata.getUsage();
        return "chat response id=%s model=%s inputTokens=%s outputTokens=%s totalTokens=%s finish=%s"
                .formatted(
                        blankToUnknown(metadata.getId()),
                        blankToUnknown(metadata.getModel()),
                        usage == null ? "?" : usage.getPromptTokens(),
                        usage == null ? "?" : usage.getCompletionTokens(),
                        usage == null ? "?" : usage.getTotalTokens(),
                        finishReasonsOf(response));
    }

    /**
     * Why the model stopped — the one field separating a good answer from a silently truncated one, as
     * a {@code MAX_TOKENS} or {@code SAFETY} stop returns a partial body over a successful call.
     */
    private static String finishReasonsOf(ChatResponse response) {
        List<Generation> results = response.getResults();
        if (results == null || results.isEmpty()) {
            return "none";
        }
        return results.stream()
                .map(generation -> generation.getMetadata() == null
                        ? "?"
                        : blankToUnknown(generation.getMetadata().getFinishReason()))
                .collect(Collectors.joining(","));
    }

    private static String promptIdOf(ChatClientRequest request) {
        Object promptId = request.context() == null ? null : request.context().get(PROMPT_ID_ATTRIBUTE);
        return promptId == null ? "unattributed" : blankToUnknown(promptId.toString());
    }

    private static String blankToUnknown(String value) {
        return value == null || value.isBlank() ? "?" : value;
    }
}
