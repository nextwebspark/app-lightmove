package app.lightmove.api.core.llm.model;

import java.util.ArrayList;
import java.util.List;
import org.springframework.core.io.Resource;

/**
 * How one prompt is guarded and its answer checked: {@code promptId} names the feature in the log, {@code answerSchema}
 * the JSON Schema the reply must fit (re-asked once; null for prose), and {@code blockedAnswer} the guard's reply, which
 * with a schema must <b>bind</b> to the reply type or a block reads as the provider down.
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
     * A prompt answering as a document. {@code blockedAnswer} is required rather than defaulted
     * because only the calling feature knows the type the reply binds to.
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

    /** Additive on purpose, and named for it: replacing would let a call drop an earlier one's phrases. */
    public PromptGuardSpec alsoRefusing(List<String> phrases) {
        if (phrases == null || phrases.isEmpty()) {
            return this;
        }
        List<String> combined = new ArrayList<>(additionalInjectionPhrases);
        combined.addAll(phrases);
        return new PromptGuardSpec(promptId, answerSchema, blockedAnswer, combined);
    }
}
