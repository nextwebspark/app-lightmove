package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.tool.TurnRecorder;
import org.springaicommunity.agent.tools.AskUserQuestionTool;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The library's {@code AskUserQuestionTool}, made to end the answer rather than wait for one. An ask is one
 * request that closes at 50 seconds and may be answered on another instance, so the questions are recorded
 * for the panel's card and the loop returns directly; the consultant's choices are the thread's next
 * question.
 */
final class AskUserQuestionCallback implements ToolCallback {

    /** Read by the model only when it called this beside another tool, so the loop could not end here. */
    static final String SHOWN_TO_CONSULTANT = "The questions are on screen for the consultant. End your answer "
            + "now with one short line; their choices arrive as their next message.";

    private static final String ANSWERS_PARAMETER = "answers";

    private final ToolCallback delegate;
    private final ToolDefinition definition;

    private AskUserQuestionCallback(ToolCallback delegate, ToolDefinition definition) {
        this.delegate = delegate;
        this.definition = definition;
    }

    static ToolCallback forAsk(TurnRecorder recorder, ObjectMapper json) {
        ToolCallback library = ToolCallbacks.from(AskUserQuestionTool.builder()
                .questionHandler(recorder::ask)
                .answersValidation(false)
                .build())[0];
        ToolDefinition original = library.getToolDefinition();
        return new AskUserQuestionCallback(library, ToolDefinition.builder()
                .name(original.name())
                .description(original.description())
                .inputSchema(withoutAnswers(original.inputSchema(), json))
                .build());
    }

    /**
     * {@code answers} is the library's slot for a host that collects them in the same call. Offered to the
     * model, it would invite it to answer its own questions.
     */
    private static String withoutAnswers(String inputSchema, ObjectMapper json) {
        ObjectNode schema = (ObjectNode) json.readTree(inputSchema);
        if (schema.get("properties") instanceof ObjectNode properties) {
            properties.remove(ANSWERS_PARAMETER);
        }
        if (schema.get("required") instanceof ArrayNode required) {
            for (int index = required.size() - 1; index >= 0; index--) {
                if (ANSWERS_PARAMETER.equals(required.get(index).asString())) {
                    required.remove(index);
                }
            }
        }
        return json.writeValueAsString(schema);
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return definition;
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return ToolMetadata.builder().returnDirect(true).build();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        delegate.call(toolInput, toolContext);
        return SHOWN_TO_CONSULTANT;
    }
}
