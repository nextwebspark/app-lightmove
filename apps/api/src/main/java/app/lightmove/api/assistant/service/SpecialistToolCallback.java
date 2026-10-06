package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.model.AssistantTurn;
import app.lightmove.api.assistant.tool.AssistantToolContext;
import app.lightmove.api.assistant.tool.TurnRecorder;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * A specialist offered to the supervisor's model as a tool, built per ask because it carries that ask's
 * question and history. Whom it acts for comes from the {@link ToolContext} the server set, never from
 * the model's input, which carries only what the supervisor wants asked.
 */
public class SpecialistToolCallback implements ToolCallback {

    private static final String INPUT_SCHEMA = """
            {"type":"object","properties":{"task":{"type":"string",
            "description":"What the consultant needs from this specialist, in a sentence."}},
            "required":["task"]}""";

    private final AssistantSpecialist specialist;
    private final AssistantModelCall model;
    private final ObjectMapper json;
    private final List<AssistantTurn> history;
    private final String question;
    private final int maxCallsPerAsk;
    private final ToolDefinition definition;

    public SpecialistToolCallback(AssistantSpecialist specialist, AssistantModelCall model, ObjectMapper json,
                                  List<AssistantTurn> history, String question, int maxCallsPerAsk) {
        this.specialist = specialist;
        this.model = model;
        this.json = json;
        this.history = history;
        this.question = question;
        this.maxCallsPerAsk = maxCallsPerAsk;
        this.definition = ToolDefinition.builder()
                .name(specialist.toolName())
                .description(specialist.description())
                .inputSchema(INPUT_SCHEMA)
                .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return definition;
    }

    @Override
    public String call(String toolInput) {
        throw new IllegalStateException("A specialist runs only inside an ask, with its tool context");
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        AssistantToolContext context = AssistantToolContext.from(toolContext);
        if (!specialist.availableTo(context)) {
            return result("The " + specialist.domain() + " specialist is not available here.");
        }
        TurnRecorder recorder = context.recorder();
        if (recorder.consultedSpecialists().size() >= maxCallsPerAsk) {
            return result("No more specialists can be asked for this question; answer with what you have.");
        }
        recorder.consulted(specialist.domain());
        int step = recorder.startStep("Asking the " + specialist.domain() + " specialist");
        String answer = specialist.cleanAnswer(model.askSpecialist(specialist, history, withTask(toolInput), context));
        specialist.afterAnswer(context);
        recorder.finishStep(step, null);
        return result(answer);
    }

    /** Gemini's adapter refuses a tool result that is not JSON, so even prose goes back as an object. */
    private String result(String answer) {
        return json.writeValueAsString(Map.of("answer", answer));
    }

    /** The consultant's own words lead, so the specialist answers them rather than a paraphrase. */
    private String withTask(String toolInput) {
        String task = taskOf(toolInput);
        return task.isBlank() || task.equals(question) ? question : question + "\n\n(Focus: " + task + ")";
    }

    private String taskOf(String toolInput) {
        if (toolInput == null || toolInput.isBlank()) {
            return "";
        }
        JsonNode task = json.readTree(toolInput).path("task");
        return task.isString() ? task.asString().strip() : "";
    }
}
