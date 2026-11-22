package app.lightmove.api.core.llm.model;

import java.util.function.Consumer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * One call's worth of a prompt: the system text serving now, the options for its model and settings, and the
 * advisor wiring that counts the call against its version. {@code model} is null on the deployment's chat model.
 */
public record PreparedPrompt(
        String systemText,
        String model,
        ChatOptions.Builder<?> options,
        Consumer<ChatClient.AdvisorSpec> attribution
) {
}
