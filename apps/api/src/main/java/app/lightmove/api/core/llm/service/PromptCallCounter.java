package app.lightmove.api.core.llm.service;

import app.lightmove.api.core.llm.constant.AiProvider;
import app.lightmove.api.core.llm.constant.FinishKind;
import app.lightmove.api.core.llm.model.PromptCallCount;
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
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
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

    private static final String REGISTRY_PROMPT_ATTRIBUTE = "lightmove.registryPromptId";
    private static final String PROVIDER_ATTRIBUTE = "lightmove.promptProvider";

    /** What is recorded when neither the answer nor the request names a model. */
    private static final String CONFIGURED_MODEL = "configured";

    private final PromptCallCountWriter writer;

    Consumer<ChatClient.AdvisorSpec> attributing(ServedPrompt served, AiProvider provider) {
        if (served.version() == null) {
            return advisors -> { };
        }
        return advisors -> advisors
                .param(REGISTRY_PROMPT_ATTRIBUTE, served.promptId())
                .param(ChatCallLog.PROMPT_VERSION_ATTRIBUTE, served.version())
                .param(PROVIDER_ATTRIBUTE, provider)
                .advisors(this);
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
        ChatClientResponse response = chain.nextCall(request);
        try {
            count(request, response.chatResponse());
        } catch (RuntimeException failure) {
            log.error("Could not count a model call against its prompt version", failure);
        }
        return response;
    }

    private void count(ChatClientRequest request, ChatResponse response) {
        if (response == null || !(request.context().get(ChatCallLog.PROMPT_VERSION_ATTRIBUTE) instanceof Integer version)) {
            return;
        }
        Usage usage = response.getMetadata().getUsage();
        writer.add(new PromptCallCount(
                (String) request.context().get(REGISTRY_PROMPT_ATTRIBUTE),
                version,
                (AiProvider) request.context().get(PROVIDER_ATTRIBUTE),
                modelOf(request, response),
                usage.getPromptTokens(),
                usage.getCompletionTokens(),
                finishOf(response)));
    }

    private static String modelOf(ChatClientRequest request, ChatResponse response) {
        String answered = response.getMetadata().getModel();
        if (answered != null && !answered.isBlank()) {
            return answered;
        }
        ChatOptions options = request.prompt().getOptions();
        String asked = options == null ? null : options.getModel();
        return asked == null || asked.isBlank() ? CONFIGURED_MODEL : asked;
    }

    private static FinishKind finishOf(ChatResponse response) {
        List<Generation> results = response.getResults();
        if (results.isEmpty() || results.getFirst().getMetadata() == null) {
            return FinishKind.OTHER;
        }
        return FinishKind.of(results.getFirst().getMetadata().getFinishReason());
    }
}
