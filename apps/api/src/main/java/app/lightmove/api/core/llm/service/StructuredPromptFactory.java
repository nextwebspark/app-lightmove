package app.lightmove.api.core.llm.service;

import app.lightmove.api.core.llm.model.PromptGuardSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Builds a {@link StructuredPrompt} from {@code prompts/{id}-system.st} and {@code prompts/{id}-schema.json}.
 * Call it from the owner's constructor: the schema is read here, so one that will not load fails the
 * context at startup rather than every request that needed it.
 */
@Component
@RequiredArgsConstructor
public class StructuredPromptFactory {

    private final ChatClient chatClient;
    private final LlmCallPolicy llmCalls;

    /** A prompt answered as JSON. {@code blockedAnswer} is what the guard replies with, and must bind too. */
    public StructuredPrompt create(String promptId, String blockedAnswer) {
        return build(promptId, blockedAnswer, false);
    }

    /** A prompt allowed to search the web before it answers, which rules out asking for JSON natively. */
    public StructuredPrompt createSearchGrounded(String promptId, String blockedAnswer) {
        return build(promptId, blockedAnswer, true);
    }

    private StructuredPrompt build(String promptId, String blockedAnswer, boolean searchGrounded) {
        Resource system = new ClassPathResource("prompts/" + promptId + "-system.st");
        Resource schema = new ClassPathResource("prompts/" + promptId + "-schema.json");
        return new StructuredPrompt(chatClient, promptId, system,
                llmCalls.forPrompt(PromptGuardSpec.structured(promptId, schema, blockedAnswer)), searchGrounded);
    }
}
