package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.model.AssistantAnswer;
import app.lightmove.api.assistant.model.AssistantExchange;
import app.lightmove.api.assistant.model.AssistantTurnPrompt;
import app.lightmove.api.core.config.AssistantSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.llm.service.ChatCallLog;
import java.time.Duration;
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
import reactor.core.publisher.Flux;

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

    /**
     * How many chunks one batch may hold before it is flushed regardless of the clock. Gemini emits
     * small chunks, so the window almost always fires first; this only bounds a burst.
     */
    private static final int BATCH_MAX_CHUNKS = 64;

    /**
     * How long text accumulates before it becomes a row.
     *
     * <p>Not a row per token, which V65 spells out: against {@code DB_POOL_MAX} of 5 that would make
     * the event log the largest write source in the application. Not in-memory either — Cloud Run has
     * no sticky routing, so a turn on one instance and a stream on another would never meet. A few
     * hundred milliseconds reads as typing and survives a reload, which is the trade this makes.
     */
    private static final Duration BATCH_WINDOW = Duration.ofMillis(400);

    private final ChatClient chatClient;
    private final AssistantSettings settings;

    public GeminiAssistantTurnRunner(ChatClient chatClient, LightMoveProperties properties) {
        this.chatClient = chatClient;
        this.settings = properties.assistant();
    }

    @Override
    public AssistantAnswer run(AssistantTurnPrompt prompt, AssistantEventSink sink) {
        AssistantTurnPrompt windowed = prompt.withHistoryWindow(settings.historyWindow());

        // Deliberately not LlmCallPolicy.forPrompt. Its SafeGuardAdvisor refuses text matching a
        // phrase list, which is right for a spreadsheet header and wrong for conversation — a
        // researcher typing "ignore the declined ones" would be refused, and replaying a tool result
        // containing one of those phrases would block a whole turn. #429 owns what replaces it.
        // The log attribution the policy would also have set is kept, because ChatCallLog is what
        // keeps prompt and answer content out of the logs.
        //
        // .chatResponse() rather than .content(): the latter is a Flux<String> and throws away the
        // per-chunk metadata the usage and model name come from.
        Flux<ChatResponse> chunks = chatClient.prompt()
                .advisors(advisors -> advisors.param(ChatCallLog.PROMPT_ID_ATTRIBUTE, PROMPT_ID))
                .options(GoogleGenAiChatOptions.builder()
                        .model(settings.model())
                        .temperature(settings.temperature())
                        .thinkingBudget(settings.thinkingBudget())
                        .labels(Map.of("prompt", PROMPT_ID)))
                .system(windowed.systemPrompt() == null ? "" : windowed.systemPrompt())
                .messages(conversation(windowed))
                .stream()
                .chatResponse();

        return consume(chunks, sink);
    }

    /**
     * Drains the stream on the calling thread, emitting batched text as it goes.
     *
     * <p>{@code toIterable()} rather than a reactive subscription: the caller is a worker thread whose
     * whole job is this turn, and handing the result back asynchronously would only move the blocking
     * somewhere less obvious.
     *
     * <p>Usage is taken from the <b>last</b> chunk that reports any, not summed. Gemini reports a
     * running total per chunk, so adding them up would multiply the bill by the chunk count.
     * {@code UsageAccumulator} is not the tool for this either — it aggregates across tool
     * <i>rounds</i>, and there is exactly one round until the tool surface lands.
     */
    private static AssistantAnswer consume(Flux<ChatResponse> chunks, AssistantEventSink sink) {
        StringBuilder answer = new StringBuilder();
        ChatResponse lastMeasured = null;
        String model = null;
        String finishReason = null;

        for (List<ChatResponse> batch : chunks.bufferTimeout(BATCH_MAX_CHUNKS, BATCH_WINDOW)
                .toIterable()) {
            StringBuilder slice = new StringBuilder();
            for (ChatResponse response : batch) {
                String text = textOf(response);
                if (text != null) {
                    slice.append(text);
                }
                if (reportedUsage(response) != null) {
                    lastMeasured = response;
                }
                if (model == null) {
                    model = modelOf(response);
                }
                String reason = finishReasonOf(response);
                if (reason != null) {
                    finishReason = reason;
                }
            }
            if (slice.length() > 0) {
                answer.append(slice);
                sink.delta(slice.toString());
            }
        }

        // No text is a failed turn whether or not the provider billed for it. The case that actually
        // happens is the billed one: a safety block, or a MAX_TOKENS finish where the thinking budget
        // ate the output. Treating that as SUCCEEDED stores a blank answer and records nothing about
        // why, so the turn reads as though the assistant simply had nothing to say.
        if (answer.isEmpty()) {
            throw new IllegalStateException("prompt " + PROMPT_ID + " produced no text"
                    + (finishReason == null ? "" : ", finish reason " + finishReason));
        }
        return new AssistantAnswer(answer.toString(), model,
                promptTokens(lastMeasured), completionTokens(lastMeasured));
    }

    private static String textOf(ChatResponse response) {
        if (response == null || response.getResult() == null
                || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
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

    /** Why the provider stopped — {@code STOP}, {@code MAX_TOKENS}, {@code SAFETY}. */
    private static String finishReasonOf(ChatResponse response) {
        if (response == null || response.getResult() == null
                || response.getResult().getMetadata() == null) {
            return null;
        }
        String reason = response.getResult().getMetadata().getFinishReason();
        return reason == null || reason.isBlank() ? null : reason;
    }

    private static String modelOf(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }
        String model = response.getMetadata().getModel();
        return model == null || model.isBlank() ? null : model;
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
        if (response == null || response.getMetadata() == null) {
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
