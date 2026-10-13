package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.model.AssistantRoundFailed;
import app.lightmove.api.assistant.tool.TurnRecorder;
import com.google.genai.errors.ApiException;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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
 * asks again a round the connection lost, the provider throttled (after a short pause) or that
 * outlasted the round's patience, so the rounds before it are not thrown away. A round's last attempt
 * is waited for however long the model call itself allows. No round starts once the answer has passed
 * its deadline. One instance per ask.
 */
@Slf4j
class AssistantRounds implements CallAdvisor {

    static final int ATTEMPTS_PER_ROUND = 3;

    /** What Vertex answers when it is short of capacity or failing; the same request is often served at once. */
    private static final Set<Integer> PASSING_STATUSES = Set.of(408, 429, 500, 502, 503, 504);

    /** Waited before asking a refused round again, once per attempt already made. */
    private static final Duration REFUSAL_PAUSE = Duration.ofMillis(500);

    private final TurnRecorder recorder;
    private final String configuredModel;
    private final long deadlineAt;
    private final Duration patience;
    private final Executor executor;
    private int round;

    AssistantRounds(TurnRecorder recorder, String configuredModel, Duration answerDeadline, Duration patience,
                    Executor executor) {
        this.recorder = recorder;
        this.configuredModel = configuredModel;
        this.deadlineAt = System.nanoTime() + answerDeadline.toNanos();
        this.patience = patience;
        this.executor = executor;
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
        long roundStartedAt = System.nanoTime();
        for (int attempt = 1; ; attempt++) {
            long startedAt = System.nanoTime();
            boolean last = attempt == ATTEMPTS_PER_ROUND;
            try {
                ChatClientResponse response = ask(request, chain, last);
                finished(response.chatResponse(), millisSince(startedAt));
                recorder.roundTook(millisSince(roundStartedAt), attempt - 1);
                return response;
            } catch (RuntimeException failed) {
                boolean again = !last && worthAskingAgain(failed) && !outOfTime();
                log.warn("Assistant round {} failed after {}ms on attempt {}{}: {}", round, millisSince(startedAt),
                        attempt, again ? ", asking it again" : "", reasonOf(failed).toString());
                if (!again) {
                    recorder.roundTook(millisSince(roundStartedAt), attempt - 1);
                    throw new AssistantRoundFailed("Round " + round + " failed", failed);
                }
                if (reasonOf(failed) instanceof ApiException) {
                    pause(REFUSAL_PAUSE.multipliedBy(attempt));
                }
            }
        }
    }

    /**
     * Runs the attempt on another thread, so one the model never answers can be left behind; the last
     * attempt is waited for however long it takes.
     */
    private ChatClientResponse ask(ChatClientRequest request, CallAdvisorChain chain, boolean last) {
        Map<String, String> logContext = MDC.getCopyOfContextMap();
        FutureTask<ChatClientResponse> attempt = new FutureTask<>(() -> {
            if (logContext != null) {
                MDC.setContextMap(logContext);
            }
            try {
                return chain.copy(this).nextCall(request);
            } finally {
                MDC.clear();
            }
        });
        executor.execute(attempt);
        try {
            return last ? attempt.get() : attempt.get(patience.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException unanswered) {
            attempt.cancel(true);
            throw new RoundUnanswered(patience);
        } catch (ExecutionException failed) {
            throw failed.getCause() instanceof RuntimeException cause ? cause : new IllegalStateException(failed);
        } catch (InterruptedException interrupted) {
            attempt.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private static void pause(Duration pause) {
        try {
            Thread.sleep(pause);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
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

    /**
     * A timeout, a dropped connection, a throttled call or one given up waiting for, which another try
     * may survive; a refusal would only be refused again.
     */
    private static boolean worthAskingAgain(Throwable failed) {
        Throwable reason = reasonOf(failed);
        return reason instanceof IOException || reason instanceof RoundUnanswered
                || reason instanceof ApiException refused && PASSING_STATUSES.contains(refused.code());
    }

    /** The first I/O failure or provider answer in the chain, else its root: the SDK wraps a timeout three deep. */
    private static Throwable reasonOf(Throwable failed) {
        Throwable cause = failed;
        while (!(cause instanceof IOException) && !(cause instanceof ApiException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    /** An attempt the model had not answered within the round's patience. */
    private static final class RoundUnanswered extends RuntimeException {

        private RoundUnanswered(Duration patience) {
            super("no answer within " + patience.toMillis() + "ms");
        }
    }

    private static long millisSince(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
