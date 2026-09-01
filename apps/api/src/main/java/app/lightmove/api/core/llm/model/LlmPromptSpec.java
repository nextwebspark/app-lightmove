package app.lightmove.api.core.llm.model;

import java.util.List;
import org.springframework.core.io.Resource;

/**
 * What one feature's call to the model needs guarding and checking — everything
 * {@code LlmGuards} has to know, and nothing about the prompt itself.
 *
 * @param promptId       names the feature in the shared client's log line; without it every line says
 *                       only that <i>something</i> called the model
 * @param answerSchema   JSON Schema the reply must fit, or null for a call that answers in prose. When
 *                       set, an answer that does not fit is put back to the model before being given
 *                       up on
 * @param blockedAnswer  what the guard replies with in place of the model. It must carry
 *                       {@link #BLOCKED_MARKER}, and for a call with a schema it must also <b>bind</b>
 *                       — a sentence where a document is expected surfaces a block as a parse error
 *                       indistinguishable from the provider being down
 * @param extraSensitive phrases to refuse on top of the configured baseline, for a prompt whose own
 *                       vocabulary makes something dangerous that is harmless elsewhere
 */
public record LlmPromptSpec(
        String promptId,
        Resource answerSchema,
        String blockedAnswer,
        List<String> extraSensitive
) {

    /**
     * The token every blocked answer carries, whatever shape the caller needs it in.
     *
     * <p>A guard answers <i>in place of</i> the model, so without a marker of our own a block is
     * indistinguishable from a real answer — and the caller would pass a canned refusal off as the
     * model's verdict.
     */
    public static final String BLOCKED_MARKER = "__lightmove_blocked__";

    public LlmPromptSpec {
        if (promptId == null || promptId.isBlank()) {
            throw new IllegalArgumentException("An LLM prompt spec needs a prompt id to log against");
        }
        blockedAnswer = blockedAnswer == null ? BLOCKED_MARKER : blockedAnswer;
        if (!blockedAnswer.contains(BLOCKED_MARKER)) {
            throw new IllegalArgumentException(
                    "The blocked answer for " + promptId + " must carry " + BLOCKED_MARKER
                            + ", or a block cannot be told from an answer");
        }
        extraSensitive = extraSensitive == null ? List.of() : List.copyOf(extraSensitive);
    }

    /** A prompt that answers in prose: guarded, with nothing to validate the shape of. */
    public static LlmPromptSpec of(String promptId) {
        return new LlmPromptSpec(promptId, null, null, List.of());
    }

    /**
     * A prompt that answers as a document. The blocked answer is required rather than defaulted
     * because it has to bind to the same type the reply does, which only the caller knows.
     */
    public static LlmPromptSpec structured(String promptId, Resource answerSchema, String blockedAnswer) {
        if (answerSchema == null) {
            throw new IllegalArgumentException("A structured prompt spec needs an answer schema");
        }
        if (blockedAnswer == null) {
            throw new IllegalArgumentException(
                    "A structured prompt spec needs a blocked answer that binds to its reply type");
        }
        return new LlmPromptSpec(promptId, answerSchema, blockedAnswer, List.of());
    }

    public LlmPromptSpec refusing(List<String> phrases) {
        return new LlmPromptSpec(promptId, answerSchema, blockedAnswer, phrases);
    }

    /** Whether an answer is the guard's rather than the model's — see {@link #BLOCKED_MARKER}. */
    public static boolean wasBlocked(String answer) {
        return answer != null && answer.contains(BLOCKED_MARKER);
    }
}
