package app.lightmove.api.assistant.tool;

/**
 * Marks a class whose {@code @Tool} methods belong to the assistant.
 *
 * <p>A marker rather than a scan of every bean carrying {@code @Tool}: the set the assistant may
 * call is a decision, and letting it be the accident of which annotation someone reached for would
 * mean a tool joining the model's surface without anyone choosing that. It is also what
 * {@link AssistantToolset} collects, so a subject that forgets to declare a permission is caught at
 * startup.
 */
public interface AssistantToolSubject {
}
