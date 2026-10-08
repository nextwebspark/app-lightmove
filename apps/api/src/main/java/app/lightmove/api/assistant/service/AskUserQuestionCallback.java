package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.tool.AssistantQuestions;
import app.lightmove.api.assistant.tool.TurnRecorder;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
final class AskUserQuestionCallback implements ToolCallback {

    /** Read by the model only when it called this beside another tool, so the loop could not end here. */
    static final String SHOWN_TO_CONSULTANT = "The questions are on screen for the consultant. End your answer "
            + "now with one short line; their choices arrive as their next message.";

    /**
     * Nothing reached the card: the call did not parse, or no question had a header and two to four labelled
     * options. The loop still returns this directly — Spring AI reads {@code returnDirect} before the call — so
     * {@link AssistantAgent} asks again without this tool rather than show it as the answer.
     */
    static final String NOTHING_SHOWN = "No question reached the consultant: each needs the question, a header of "
            + "at most 12 characters and two to four options with labels. Answer without asking.";

    private static final String ANSWERS_PARAMETER = "answers";

    private final ToolCallback delegate;
    private final ToolDefinition definition;
    private final TurnRecorder recorder;

    private AskUserQuestionCallback(ToolCallback delegate, ToolDefinition definition, TurnRecorder recorder) {
        this.delegate = delegate;
        this.definition = definition;
        this.recorder = recorder;
    }

    static ToolCallback forAsk(TurnRecorder recorder, ObjectMapper json) {
        ToolCallback library = ToolCallbacks.from(AskUserQuestionTool.builder()
                .questionHandler(asked -> {
                    recorder.ask(AssistantQuestions.from(asked));
                    return Map.of();
                })
                .answersValidation(false)
                .build())[0];
        ToolDefinition original = library.getToolDefinition();
        return new AskUserQuestionCallback(library, ToolDefinition.builder()
                .name(original.name())
                .description(original.description())
                .inputSchema(withoutAnswers(original.inputSchema(), json))
                .build(), recorder);
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
        return call(toolInput, new ToolContext(Map.of()));
    }

    /** The library throws on a question it cannot read (a blank header, a missing label); that shows nothing. */
    @Override
    public String call(String toolInput, ToolContext toolContext) {
        try {
            delegate.call(toolInput, toolContext);
        } catch (RuntimeException unreadable) {
            log.info("Assistant questions could not be read: {}", unreadable.getMessage());
        }
        return recorder.askedQuestions() ? SHOWN_TO_CONSULTANT : NOTHING_SHOWN;
    }
}
