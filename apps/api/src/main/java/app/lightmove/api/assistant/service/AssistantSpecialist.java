package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.model.AssistantTurn;
import app.lightmove.api.assistant.tool.AssistantToolContext;
import app.lightmove.api.assistant.tool.TurnRecorder;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;

/**
 * One domain the assistant answers about — its own prompt and its own few tools, run as a nested model
 * call. The supervisor offers each one it may use to the model as a tool ({@link SpecialistToolCallback}),
 * so a domain's data rules live with its tools rather than in one prompt shared by every domain.
 *
 * <p>Within one ask the hooks run in order: {@link #prepare} for every available specialist before any
 * model call, then {@link #afterAnswer} for each one asked, once its answer is in and before the turn
 * is saved — so whatever {@code prepare} put on the {@link TurnRecorder} is there for {@code afterAnswer}.
 */
public interface AssistantSpecialist {

    /** What the supervisor's model calls it, e.g. {@code askCompanySpecialist}. */
    String toolName();

    /** The domain in a word — a step label and the audit event's {@code specialists}. */
    String domain();

    /** What the supervisor's model reads when deciding whether to ask it. */
    String description();

    /**
     * Checked by the server before the specialist is offered and again before it runs — never left to
     * the model, which only ever sees the specialists this returns true for.
     */
    boolean availableTo(AssistantToolContext context);

    /** ChatCallLog attribution and the Vertex label, so each specialist's cost is its own. */
    String promptId();

    Resource systemPrompt();

    Map<String, Object> systemParams(AssistantToolContext context);

    /** {@code @Tool} beans. */
    List<Object> tools();

    /** Called for every specialist available to an ask, before any model call. */
    default void prepare(List<AssistantTurn> history, TurnRecorder recorder) {
    }

    /** Runs after this specialist has answered, while the turn is still being assembled. */
    default void afterAnswer(AssistantToolContext context) {
    }
}
