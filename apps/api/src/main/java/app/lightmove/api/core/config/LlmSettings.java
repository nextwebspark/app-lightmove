package app.lightmove.api.core.config;

import java.util.List;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Tunables for the Google GenAI integration — {@code lightmove.llm.*}. */
public record LlmSettings(
        LlmRateLimitSettings rateLimit,

        /**
         * Ceiling on one call to the model, in milliseconds.
         *
         * <p>The Google SDK applies no bound of its own that we set, and Spring AI's retry sits on top
         * of the call rather than around the clock — so without this a hung Vertex holds a request
         * thread, and the user's browser, for as long as it likes.
         */
        @DefaultValue("20000") int requestTimeoutMs,

        /**
         * How many times an answer that does not fit its schema is put back to the model before the
         * caller gives up on it. One: the same budget as the transport retry, and a model answering
         * out of shape twice is not about to answer in shape on a third try.
         */
        @DefaultValue("1") int answerRepairAttempts,

        /**
         * Phrases with no business in text a user supplies to a prompt, refused before the call is
         * made. Short on purpose: every entry is also something somebody could one day write in a job
         * brief or a spreadsheet header, and blocking real work is its own failure.
         */
        @DefaultValue({
                "ignore previous instructions",
                "ignore all previous",
                "disregard the above",
                "disregard previous",
                "system prompt",
                "you are now"
        }) List<String> injectionPhrases
) {

    public LlmSettings {
        if (requestTimeoutMs < 1) {
            throw new IllegalArgumentException(
                    "lightmove.llm.request-timeout-ms must be positive, but was " + requestTimeoutMs);
        }
        if (answerRepairAttempts < 0) {
            throw new IllegalArgumentException(
                    "lightmove.llm.answer-repair-attempts must not be negative, but was "
                            + answerRepairAttempts);
        }
        // @DefaultValue on a List binds an operator's empty override to [""], not to [] — and a
        // blocklist holding one blank string matches every prompt, so it is refused loudly here.
        if (injectionPhrases.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException(
                    "lightmove.llm.injection-phrases must not contain a blank entry");
        }
        injectionPhrases = List.copyOf(injectionPhrases);
    }
}
