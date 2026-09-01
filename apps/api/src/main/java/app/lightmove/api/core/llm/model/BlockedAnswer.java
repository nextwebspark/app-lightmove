package app.lightmove.api.core.llm.model;

/**
 * The contract that lets a caller tell the guard's canned answer from the model's own.
 *
 * <p>A guard answers <i>in place of</i> the model, so without a marker of our own a refusal is
 * indistinguishable from a real reply — and the caller passes it off as the model's verdict.
 */
public final class BlockedAnswer {

    /** The token every blocked answer carries, whatever shape the calling feature needs it in. */
    public static final String MARKER = "__lightmove_blocked__";

    private BlockedAnswer() {
    }

    /** Whether this answer came from the guard rather than the model. */
    public static boolean matches(String answer) {
        return answer != null && answer.contains(MARKER);
    }

    /**
     * Refuses a blocked answer the caller could not recognise, at the point it is configured rather
     * than on the request that would have been misread.
     *
     * @param promptId names the prompt whose configuration is wrong, since the message is a startup
     *                 failure a developer has to trace back to one call site
     */
    static String requireRecognisable(String promptId, String answer) {
        if (!matches(answer)) {
            throw new IllegalArgumentException(
                    "The blocked answer for " + promptId + " must carry " + MARKER
                            + ", or a block cannot be told from an answer");
        }
        return answer;
    }
}
