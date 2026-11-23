package app.lightmove.api.core.llm.service;

import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.llm.constant.FinishKind;
import app.lightmove.api.core.llm.model.PromptTrialResult;
import app.lightmove.api.core.llm.model.ServedPrompt;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs a {@link PromptTrial} against one version rather than the one serving: for the length of the run, on this
 * thread only, {@link PromptCalls} answers that prompt with the version under trial and the model's answer is
 * kept. Nothing else sees it, so a trial never changes what any other call is served.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptTrialRunner implements CallAdvisor {

    private static final ThreadLocal<Run> RUNNING = new ThreadLocal<>();

    private final ObjectMapper json;

    public PromptTrialResult run(PromptTrial trial, ServedPrompt version) {
        if (!trial.promptId().equals(version.promptId())) {
            throw new IllegalArgumentException("A trial of " + trial.promptId() + " cannot run a version of "
                    + version.promptId());
        }
        return run(version, trial::run);
    }

    /**
     * Answers {@code asking} with {@code version} wherever it asks that version's prompt, and keeps what the model
     * said: a Try it's fictional input, or one eval case through its subject.
     */
    public PromptTrialResult run(ServedPrompt version, Runnable asking) {
        Run run = new Run(version);
        RUNNING.set(run);
        long startedAt = System.nanoTime();
        try {
            asking.run();
        } catch (ApiException refused) {
            if (run.rawAnswer == null) {
                throw refused;
            }
            log.warn("Trial of prompt {} version {} ended in its feature refusing the answer: {}",
                    version.promptId(), version.version(), refused.getCode());
        } catch (RuntimeException failure) {
            // A feature that cannot use an answer throws: what the model said is still what Try it is asked to show.
            log.warn("Trial of prompt {} version {} ended in its feature refusing the answer: {}",
                    version.promptId(), version.version(), failure.toString());
        } finally {
            RUNNING.remove();
        }
        return new PromptTrialResult(run.rawAnswer, run.parsedAnswer == null ? null : json.valueToTree(run.parsedAnswer),
                run.model, run.inputTokens, run.outputTokens, run.finish, (System.nanoTime() - startedAt) / 1_000_000);
    }

    /** The version under trial on this thread, when it is a version of {@code promptId}. */
    ServedPrompt versionUnderTrial(String promptId) {
        Run run = RUNNING.get();
        return run != null && run.version.promptId().equals(promptId) ? run.version : null;
    }

    /** Records the answer, and names the version on the call's log line; the call counter is never attached. */
    Consumer<ChatClient.AdvisorSpec> recording() {
        Run run = RUNNING.get();
        return run == null ? advisors -> { } : advisors -> advisors
                .param(ChatCallLog.PROMPT_VERSION_ATTRIBUTE, run.version.version())
                .advisors(this);
    }

    /** What the feature bound the answer to. A no-op outside a trial. */
    void bound(Object answer) {
        Run run = RUNNING.get();
        if (run != null) {
            run.parsedAnswer = answer;
        }
    }

    @Override
    public String getName() {
        return "Prompt trial recorder";
    }

    /** Beside the call counter, innermost: after a schema repair, the answer kept is the one that was bound. */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 900;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientResponse response = chain.nextCall(request);
        Run run = RUNNING.get();
        ChatResponse answered = response.chatResponse();
        if (run != null && answered != null) {
            List<Generation> results = answered.getResults();
            Generation first = results.isEmpty() ? null : results.getFirst();
            run.rawAnswer = first == null ? null : first.getOutput().getText();
            run.finish = first == null || first.getMetadata() == null
                    ? FinishKind.OTHER : FinishKind.of(first.getMetadata().getFinishReason());
            run.model = answered.getMetadata().getModel();
            run.inputTokens += answered.getMetadata().getUsage().getPromptTokens();
            run.outputTokens += answered.getMetadata().getUsage().getCompletionTokens();
        }
        return response;
    }

    private static final class Run {
        private final ServedPrompt version;
        private String rawAnswer;
        private Object parsedAnswer;
        private String model;
        private long inputTokens;
        private long outputTokens;
        private FinishKind finish = FinishKind.OTHER;

        private Run(ServedPrompt version) {
            this.version = version;
        }
    }
}
