package app.lightmove.api;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.LlmRateLimitSettings;
import app.lightmove.api.core.config.LlmSettings;
import app.lightmove.api.core.llm.service.LlmCallPolicy;
import app.lightmove.api.core.llm.service.StructuredPromptFactory;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

/**
 * {@link LlmCallPolicy} wired the way the shipped configuration wires it, for the unit tests that
 * build a model-calling service by hand. Real phrases and a real repair budget — a stub here would
 * test the stub.
 */
public final class TestLlmCallPolicy {

    private TestLlmCallPolicy() {
    }

    public static LlmCallPolicy asShipped() {
        return new LlmCallPolicy(new LightMoveProperties(null, null, null, null, null,
                new LlmSettings(new LlmRateLimitSettings(true, 10, 20, 10), 20_000, 1,
                        List.of("ignore previous instructions", "system prompt", "you are now")),
                null, null, null, null, null, null, null, null, null));
    }

    /** Structured prompts over {@code model}, loaded from the shipped prompt files and guarded as shipped. */
    public static StructuredPromptFactory promptsOver(ChatModel model) {
        return promptsOver(ChatClient.builder(model).build());
    }

    public static StructuredPromptFactory promptsOver(ChatClient chatClient) {
        return new StructuredPromptFactory(chatClient, asShipped());
    }
}
