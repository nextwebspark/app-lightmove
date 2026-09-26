package app.lightmove.api.core.llm.service;

import java.util.Map;
import java.util.function.Consumer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.core.io.Resource;

/**
 * One prompt whose answer binds to a record: its system text, its guard and schema advisors, and the
 * options every such call is made with. Built once per caller by {@link StructuredPromptFactory}.
 */
public final class StructuredPrompt {

    /** Extraction and mapping have one right answer, so variance buys only answers that will not bind. */
    private static final double TEMPERATURE = 0.0;

    /**
     * Asked for natively, so there is no markdown fence on the answer. Without it Gemini wrapped its
     * reply in a {@code ```json} fence, {@code StructuredOutputValidationAdvisor} handed that text to
     * Jackson verbatim, and every call failed validation on the leading backtick — then spent its
     * repair attempt re-asking a question whose answer came back fenced identically.
     */
    private static final String ANSWER_MIME_TYPE = "application/json";

    /**
     * No reasoning step: reading a document onto a fixed field list is not a problem thinking
     * improves, and left at the default the model deliberated before every answer — billed output
     * tokens and seconds a person is waiting on.
     */
    private static final int THINKING_BUDGET = 0;

    private final ChatClient chatClient;
    private final String promptId;
    private final Resource systemPrompt;
    private final Consumer<ChatClient.AdvisorSpec> guarded;
    private final boolean searchGrounded;

    StructuredPrompt(ChatClient chatClient, String promptId, Resource systemPrompt,
                     Consumer<ChatClient.AdvisorSpec> guarded, boolean searchGrounded) {
        this.chatClient = chatClient;
        this.promptId = promptId;
        this.systemPrompt = systemPrompt;
        this.guarded = guarded;
        this.searchGrounded = searchGrounded;
    }

    /** The model's answer bound to {@code answerType}, or null when it answered nothing. */
    public <T> T ask(Class<T> answerType, Consumer<ChatClient.PromptUserSpec> user) {
        return chatClient.prompt()
                .advisors(guarded)
                .options(options())
                .system(systemPrompt)
                .user(user)
                .call()
                .entity(answerType);
    }

    private GoogleGenAiChatOptions.Builder options() {
        GoogleGenAiChatOptions.Builder options = GoogleGenAiChatOptions.builder()
                .temperature(TEMPERATURE)
                .thinkingBudget(THINKING_BUDGET)
                .labels(Map.of("prompt", promptId));
        // Google Search grounding cannot be combined with a JSON response type on Gemini 2.5, so a
        // grounded answer's shape comes from the prompt and the schema advisor instead.
        return searchGrounded ? options.googleSearchRetrieval(true) : options.responseMimeType(ANSWER_MIME_TYPE);
    }
}
