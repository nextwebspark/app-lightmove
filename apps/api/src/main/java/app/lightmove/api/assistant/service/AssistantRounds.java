package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.model.AssistantRoundFailed;
import app.lightmove.api.assistant.tool.TurnRecorder;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.google.genai.metadata.GoogleGenAiUsage;

/**
 * Wraps each model round of one ask, inside the tool loop: times and logs it, counts its tokens, and
 * asks a round the connection lost once more, so the rounds before it are not thrown away. No round
 * starts once the answer has passed its deadline. One instance per ask.
 */
@Slf4j
class AssistantRounds implements CallAdvisor {

    private static final int ATTEMPTS_PER_ROUND = 2;

    private final TurnRecorder recorder;
    private final String configuredModel;
    private final long deadlineAt;
    private int round;

    AssistantRounds(TurnRecorder recorder, String configuredModel, Duration answerDeadline) {
        this.recorder = recorder;
        this.configuredModel = configuredModel;
        this.deadlineAt = System.nanoTime() + answerDeadline.toNanos();
    }

    @Override
    public String getName() {
        return "Assistant rounds";
    }

    /** After the tool loop, so the loop calls it once per round. */
    @Override
    public int getOrder() {
        return ToolCallingAdvisor.DEFAULT_ORDER + 100;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        round++;
        if (outOfTime()) {
            throw new AssistantRoundFailed("The answer passed its deadline before round " + round, null);
        }
        for (int attempt = 1; ; attempt++) {
            long startedAt = System.nanoTime();
            try {
                ChatClientResponse response = chain.copy(this).nextCall(request);
                finished(response.chatResponse(), millisSince(startedAt));
                return response;
            } catch (RuntimeException failed) {
                boolean again = attempt < ATTEMPTS_PER_ROUND && lostConnection(failed) && !outOfTime();
                log.warn("Assistant round {} failed after {}ms on attempt {}{}: {}", round, millisSince(startedAt),
                        attempt, again ? ", asking it again" : "", reasonOf(failed).toString());
                if (!again) {
                    throw new AssistantRoundFailed("Round " + round + " failed", failed);
                }
            }
        }
    }

    private void finished(ChatResponse response, long tookMs) {
        if (response == null) {
            log.info("Assistant round {} took {}ms and answered nothing", round, tookMs);
            return;
        }
        Usage usage = response.getMetadata().getUsage();
        String model = response.getMetadata().getModel();
        recorder.spent(model == null || model.isBlank() ? configuredModel : model,
                usage.getPromptTokens(), usage.getCompletionTokens());
        log.info("Assistant round {} took {}ms, {} tokens in, {} out and {} thinking, {}", round, tookMs,
                usage.getPromptTokens(), usage.getCompletionTokens(), thinkingTokensOf(usage), askedFor(response));
    }

    private static int thinkingTokensOf(Usage usage) {
        Integer thinking = usage instanceof GoogleGenAiUsage gemini ? gemini.getThoughtsTokenCount() : null;
        return thinking == null ? 0 : thinking;
    }

    private static String askedFor(ChatResponse response) {
        List<String> tools = response.getResults().stream()
                .flatMap(generation -> generation.getOutput().getToolCalls().stream())
                .map(AssistantMessage.ToolCall::name)
                .toList();
        return tools.isEmpty() ? "wrote the answer" : "called " + String.join(", ", tools);
    }

    private boolean outOfTime() {
        return System.nanoTime() - deadlineAt >= 0;
    }

    /** A timeout or a dropped connection, which a second try may survive; a refusal would only be refused again. */
    private static boolean lostConnection(Throwable failed) {
        return reasonOf(failed) instanceof IOException;
    }

    /** The first I/O failure in the chain, else its root: the SDK wraps a timeout three deep. */
    private static Throwable reasonOf(Throwable failed) {
        Throwable cause = failed;
        while (!(cause instanceof IOException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static long millisSince(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
