package app.lightmove.api.core.llm.service;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * The {@link ChatModel} of a deployment with AI switched off ({@code lightmove.llm.enabled: false}):
 * every call fails at once, which each caller already treats as Vertex being unreachable — the
 * heuristic fallback, an empty reading, or {@code ASSISTANT_UNAVAILABLE}.
 */
public class DisabledChatModel implements ChatModel {

    @Override
    public ChatResponse call(Prompt prompt) {
        throw new IllegalStateException("AI is switched off here (lightmove.llm.enabled=false)");
    }
}
