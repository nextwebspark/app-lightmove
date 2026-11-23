package app.lightmove.api.core.llm.service;

import app.lightmove.api.core.llm.constant.FinishKind;
import app.lightmove.api.core.llm.model.PromptCallCount;
import app.lightmove.api.core.llm.model.PromptCallTrace;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * Counts every answer of the model against the prompt version it was asked with. Innermost, so a schema repair's
 * second ask and each round of a tool loop are each a call. A prompt served from its bundled file has no version
 * and is not counted.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptCallCounter implements CallAdvisor {

    private static final String TRACE_ATTRIBUTE = "lightmove.promptCallTrace";

    private final PromptCallCountWriter writer;
    private final CallAdvisor refusals = new GuardRefusalCounter();

    /** Nothing when {@code trace} is null: the bundled file is serving, and has no version to count against. */
    Consumer<ChatClient.AdvisorSpec> attributing(PromptCallTrace trace) {
        if (trace == null) {
            return advisors -> { };
        }
        return advisors -> advisors
                .param(TRACE_ATTRIBUTE, trace)
                .param(ChatCallLog.PROMPT_VERSION_ATTRIBUTE, trace.version().version())
                .advisors(this, refusals);
    }

    /** The model answered, and its answer could not be read into what the feature expects. */
    void countSchemaFailure(PromptCallTrace trace) {
        add(PromptCallCount.schemaFailure(trace));
    }

    @Override
    public String getName() {
        return "Prompt call counter";
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 1000;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        PromptCallTrace trace = traceOf(request);
        ChatClientResponse response;
        try {
            response = chain.nextCall(request);
        } catch (RuntimeException failure) {
            if (trace != null) {
                trace.modelCallFailed();
            }
            throw failure;
        }
        ChatResponse answer = response.chatResponse();
        if (trace != null && answer != null) {
            try {
                Usage usage = answer.getMetadata().getUsage();
                boolean reask = trace.answeredBy(answer.getMetadata().getModel());
                writer.add(PromptCallCount.answered(trace, usage.getPromptTokens(), usage.getCompletionTokens(),
                        finishOf(answer), reask));
            } catch (RuntimeException failure) {
                log.error("Could not count a model call against its prompt version", failure);
            }
        }
        return response;
    }

    private void add(PromptCallCount count) {
        try {
            writer.add(count);
        } catch (RuntimeException failure) {
            log.error("Could not count a model call against its prompt version", failure);
        }
    }

    private static PromptCallTrace traceOf(ChatClientRequest request) {
        return request.context().get(TRACE_ATTRIBUTE) instanceof PromptCallTrace trace ? trace : null;
    }

    private static FinishKind finishOf(ChatResponse response) {
        List<Generation> results = response.getResults();
        if (results.isEmpty() || results.getFirst().getMetadata() == null) {
            return FinishKind.OTHER;
        }
        return FinishKind.of(results.getFirst().getMetadata().getFinishReason());
    }

    /**
     * Outside the injection guard, which answers in the model's place without calling the rest of the chain: a call
     * that comes back with the model never asked is the guard's refusal, the only advisor that answers for it.
     */
    private final class GuardRefusalCounter implements CallAdvisor {

        @Override
        public String getName() {
            return "Guard refusal counter";
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE + 1000;
        }

        @Override
        public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
            ChatClientResponse response = chain.nextCall(request);
            PromptCallTrace trace = traceOf(request);
            if (trace != null && trace.wasNeverAnswered()) {
                add(PromptCallCount.guardRefusal(trace));
            }
            return response;
        }
    }
}
