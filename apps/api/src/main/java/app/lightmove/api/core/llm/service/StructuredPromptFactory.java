package app.lightmove.api.core.llm.service;

import app.lightmove.api.core.llm.model.PromptGuardSpec;
import app.lightmove.api.core.platformconfig.service.PromptTexts;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Builds a {@link StructuredPrompt} from {@code prompts/{id}-system.st} and {@code prompts/{id}-schema.json}.
 * Call it from the owner's constructor: the schema is read here, so one that will not load fails the
 * context at startup rather than every request that needed it. The system text is asked of {@link PromptTexts}
 * on every call, so a super admin's override is the next call's; the schema is never overridden.
 */
@Component
@RequiredArgsConstructor
public class StructuredPromptFactory {

    private final ChatClient chatClient;
    private final LlmCallPolicy llmCalls;
    private final PromptTexts promptTexts;

    /** A prompt answered as JSON. {@code blockedAnswer} is what the guard replies with, and must bind too. */
    public StructuredPrompt create(String promptId, String blockedAnswer) {
        Resource schema = new ClassPathResource("prompts/" + promptId + "-schema.json");
        return new StructuredPrompt(chatClient, promptId, promptTexts,
                llmCalls.forPrompt(PromptGuardSpec.structured(promptId, schema, blockedAnswer)));
    }
}
