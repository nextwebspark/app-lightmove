package app.lightmove.api.core.llm.service;

import app.lightmove.api.core.llm.model.PromptCallSpec;
import app.lightmove.api.core.llm.model.PromptGuardSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Builds a {@link StructuredPrompt} over the prompt {@code id} and its {@code prompts/{id}-schema.json}.
 * Call it from the owner's constructor: the schema is read here, so one that will not load fails the
 * context at startup rather than every request that needed it.
 */
@Component
@RequiredArgsConstructor
public class StructuredPromptFactory {

    private final ChatClient chatClient;
    private final LlmCallPolicy llmCalls;
    private final PromptCalls prompts;

    /** A prompt answered as JSON. {@code blockedAnswer} is what the guard replies with, and must bind too. */
    public StructuredPrompt create(String promptId, String blockedAnswer) {
        return create(promptId, blockedAnswer, null);
    }

    /** The same, answered by {@code defaultModel} rather than the deployment's where the serving version names none. */
    public StructuredPrompt create(String promptId, String blockedAnswer, String defaultModel) {
        Resource schema = new ClassPathResource("prompts/" + promptId + "-schema.json");
        return new StructuredPrompt(chatClient, prompts, PromptCallSpec.structured(promptId, defaultModel),
                llmCalls.forPrompt(PromptGuardSpec.structured(promptId, schema, blockedAnswer)));
    }
}
