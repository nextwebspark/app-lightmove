package app.lightmove.api.assistant.tool;

import java.util.Map;
import java.util.UUID;
import org.springframework.ai.chat.model.ToolContext;

/**
 * What a tool knows about the request it runs in, set by the server and never by the model: whose
 * workspace, which project, and where its steps and proposed card are recorded. The project was
 * authorised once at the ask endpoint, which is why no tool takes a project id as an argument.
 */
public record AssistantToolContext(UUID workspaceId, UUID projectId, TurnRecorder recorder) {

    private static final String KEY = "assistant";

    public Map<String, Object> asMap() {
        return Map.of(KEY, this);
    }

    public static AssistantToolContext from(ToolContext toolContext) {
        return (AssistantToolContext) toolContext.getContext().get(KEY);
    }
}
