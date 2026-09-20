package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.model.AssistantAnswer;
import app.lightmove.api.assistant.model.AssistantExchange;
import app.lightmove.api.assistant.model.AssistantTurnPrompt;
import app.lightmove.api.core.config.AssistantSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.llm.service.ChatCallLog;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Service;

/**
 * One assistant turn, run against Gemini through the shared {@link ChatClient}.
 *
 * <p>Pins its own model rather than taking the application's. The two are different workloads — the
 * extraction prompts classify and a turn plans — and {@code ChatClientConfig} explains why this has
 * to happen per call: the bean's default options and a call's {@code .options(...)} are one setter,
 * so whatever a call names, it must name all of.
 */
@Service
public class GeminiAssistantTurnRunner implements AssistantTurnRunner {

    /** Log attribution, and the label Vertex bills this workload under. */
    static final String PROMPT_ID = "assistant-turn";

    private final ChatClient chatClient;
    private final AssistantSettings settings;

    public GeminiAssistantTurnRunner(ChatClient chatClient, LightMoveProperties properties) {
        this.chatClient = chatClient;
        this.settings = properties.assistant();
    }

    @Override
    public AssistantAnswer run(AssistantTurnPrompt prompt) {
        AssistantTurnPrompt windowed = prompt.withHistoryWindow(settings.historyWindow());

        // Deliberately not LlmCallPolicy.forPrompt. Its SafeGuardAdvisor refuses text matching a
        // phrase list, which is right for a spreadsheet header and wrong for conversation — a
        // researcher typing "ignore the declined ones" would be refused, and replaying a tool result
        // containing one of those phrases would block a whole turn. #429 owns what replaces it.
        // Nothing reaches this class from a request yet, so there is no user text to guard here.
        // The log attribution the policy would also have set is kept, because ChatCallLog is what
        // keeps prompt and answer content out of the logs.
        ChatResponse response = chatClient.prompt()
                .advisors(advisors -> advisors.param(ChatCallLog.PROMPT_ID_ATTRIBUTE, PROMPT_ID))
                .options(GoogleGenAiChatOptions.builder()
                        .model(settings.model())
                        .temperature(settings.temperature())
                        .thinkingBudget(settings.thinkingBudget())
                        .labels(Map.of("prompt", PROMPT_ID)))
                .system(windowed.systemPrompt() == null ? "" : windowed.systemPrompt())
                .messages(conversation(windowed))
                .call()
                .chatResponse();

        return answerFrom(response);
    }

    /** The thread so far plus the new question, in the order the model should read them. */
    private static List<Message> conversation(AssistantTurnPrompt prompt) {
        List<Message> messages = new ArrayList<>(prompt.history().size() * 2 + 1);
        for (AssistantExchange exchange : prompt.history()) {
            messages.add(new UserMessage(exchange.question()));
            // A turn that failed has a question and no answer. It stays in the history as the
            // question it was: dropping the pair would hide from the model that it was already
            // asked, and inventing an answer for it would be worse.
            if (exchange.answer() != null && !exchange.answer().isBlank()) {
                messages.add(new AssistantMessage(exchange.answer()));
            }
        }
        messages.add(new UserMessage(prompt.question()));
        return messages;
    }

    private static AssistantAnswer answerFrom(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            throw new IllegalStateException("prompt " + PROMPT_ID + " answered with nothing");
        }
        String text = response.getResult().getOutput().getText();
        return new AssistantAnswer(text == null ? "" : text, modelOf(response),
                promptTokens(response), completionTokens(response));
    }

    private static String modelOf(ChatResponse response) {
        return response.getMetadata() == null ? null : response.getMetadata().getModel();
    }

    private static Integer promptTokens(ChatResponse response) {
        Usage usage = reportedUsage(response);
        return usage == null ? null : usage.getPromptTokens();
    }

    private static Integer completionTokens(ChatResponse response) {
        Usage usage = reportedUsage(response);
        return usage == null ? null : usage.getCompletionTokens();
    }

    /**
     * The usage block, or null where nothing was actually measured.
     *
     * <p>A null check alone is not enough, and the difference is a billing one. Spring AI answers a
     * response carrying no usage with an {@code EmptyUsage} rather than a null, and that reports
     * <b>zero</b> tokens — so an unmeasured turn would land in the spend total as a turn that cost
     * nothing, which is the one wrong answer that never looks wrong. A turn always sends a prompt,
     * so zero input tokens is a missing measurement and not a free call.
     */
    private static Usage reportedUsage(ChatResponse response) {
        if (response.getMetadata() == null) {
            return null;
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage == null) {
            return null;
        }
        Integer prompt = usage.getPromptTokens();
        return prompt == null || prompt == 0 ? null : usage;
    }
}
