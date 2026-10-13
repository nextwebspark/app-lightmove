package app.lightmove.api.assistant.model;

/**
 * A model round that could not be had: the model failed on it, or the answer ran out of time before it.
 * The one failure an ask may still answer through, with what its tools had found.
 */
public class AssistantRoundFailed extends RuntimeException {

    public AssistantRoundFailed(String message, Throwable cause) {
        super(message, cause);
    }
}
