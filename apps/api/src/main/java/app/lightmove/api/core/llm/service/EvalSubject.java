package app.lightmove.api.core.llm.service;

import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * One prompt's way into an eval: a stored case becomes the model's input by going through the calling feature's
 * own code, so the user message, the redaction, the guard, the schema check and the repair are production's.
 * Implemented beside each caller, like its {@link PromptTrial}.
 *
 * <p><b>An implementation reads no table.</b> A case is synthetic and arrives whole; the platform sits inside no
 * workspace.
 *
 * <p>{@link #minimumRepeats} and {@link #isGateReady} are read by the console's runs and their put-live check
 * (#1320).
 */
public interface EvalSubject {

    String promptId();

    /**
     * What stops a case from being run or scored, each a sentence naming the field at fault; empty when nothing
     * does. Asked before a case is stored, so a run never meets one it cannot read.
     */
    List<String> problemsOf(JsonNode input, JsonNode expected);

    /**
     * Asks the model once with a case's input. Nothing is returned: a run calls this inside
     * {@link PromptTrialRunner}, which reads the parsed answer and the spend off the call itself.
     */
    void answer(JsonNode input);

    /** The rules every answer of this prompt is held to, in the order a compare view lists them. */
    List<EvalCheck> checks();

    /** How many times a case is answered at the least: more than once where answers vary even at temperature 0. */
    default int minimumRepeats() {
        return 1;
    }

    /**
     * Whether a run on this prompt's cases may let a version go live without a written reason. False until a person
     * has reviewed the cases the check would rest on, so a subject opts in; a run still scores and reports either
     * way.
     */
    default boolean isGateReady() {
        return false;
    }
}
