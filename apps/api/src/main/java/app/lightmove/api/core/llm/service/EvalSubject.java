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
 */
public interface EvalSubject {

    String promptId();

    /**
     * What stops a case from being run or scored, each a sentence naming the field at fault; empty when nothing
     * does. Asked before a case is stored, so a run never meets one it cannot read.
     */
    List<String> problemsOf(JsonNode input, JsonNode expected);

    /** Asks the model once with a case's input. */
    void answer(JsonNode input);
}
