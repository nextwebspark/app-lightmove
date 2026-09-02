package app.lightmove.api.core.llm.model;

import java.util.ArrayList;
import java.util.List;
import org.springframework.core.io.Resource;

/**
 * How one prompt is guarded and its answer checked — everything {@code LlmCallPolicy} needs, and
 * nothing about the prompt's own text.
 *
 * @param promptId                   names the feature in the shared client's log line; without it
 *                                   every line says only that <i>something</i> called the model
 * @param answerSchema               JSON Schema the reply must fit, or null for a prompt answering in
 *                                   prose. When set, an answer that does not fit is put back to the
 *                                   model before being given up on
 * @param blockedAnswer              what the guard replies with in place of the model. For a prompt
 *                                   with a schema it must also <b>bind</b> to the reply type — a
 *                                   sentence where a document is expected surfaces a block as a parse
 *                                   error indistinguishable from the provider being down
 * @param additionalInjectionPhrases refused on top of the configured baseline, for a prompt whose own
 *                                   vocabulary makes something dangerous that is harmless elsewhere
 */
public record PromptGuardSpec(
        String promptId,
        Resource answerSchema,
        String blockedAnswer,
        List<String> additionalInjectionPhrases
) {

    public PromptGuardSpec {
        if (promptId == null || promptId.isBlank()) {
            throw new IllegalArgumentException("A prompt guard spec needs a prompt id to log against");
        }
        blockedAnswer = BlockedAnswer.requireRecognisable(
                promptId, blockedAnswer == null ? BlockedAnswer.MARKER : blockedAnswer);
        additionalInjectionPhrases = additionalInjectionPhrases == null
                ? List.of()
                : List.copyOf(additionalInjectionPhrases);
    }

    /** A prompt answering in prose: guarded, with no shape to hold the answer to. */
    public static PromptGuardSpec prose(String promptId) {
        return new PromptGuardSpec(promptId, null, null, List.of());
    }

    /**
     * A prompt answering as a document.
     *
     * @param blockedAnswer required rather than defaulted because it has to bind to the same type the
     *                      reply does, which only the calling feature knows
     */
    public static PromptGuardSpec structured(String promptId, Resource answerSchema, String blockedAnswer) {
        if (answerSchema == null) {
            throw new IllegalArgumentException("A structured prompt guard spec needs an answer schema");
        }
        if (blockedAnswer == null) {
            throw new IllegalArgumentException(
                    "A structured prompt guard spec needs a blocked answer that binds to its reply type");
        }
        return new PromptGuardSpec(promptId, answerSchema, blockedAnswer, List.of());
    }

    /**
     * The same prompt, refusing these phrases as well as the ones it already refuses.
     *
     * <p>Additive on purpose, and named for it: replacing would make two calls silently drop the first
     * one's phrases, and a guard that quietly stops refusing something is the failure worth avoiding.
     */
    public PromptGuardSpec alsoRefusing(List<String> phrases) {
        if (phrases == null || phrases.isEmpty()) {
            return this;
        }
        List<String> combined = new ArrayList<>(additionalInjectionPhrases);
        combined.addAll(phrases);
        return new PromptGuardSpec(promptId, answerSchema, blockedAnswer, combined);
    }
}
