package app.lightmove.api.assistant.model;

/**
 * One completed turn of a thread, as history for the next one.
 *
 * <p>A question and its answer rather than a role-tagged message, because that is what the schema
 * holds: {@code app_lm_assistant_turn} is the exchange, so history needs no reassembly and no role
 * enum that could disagree with the rows.
 */
public record AssistantExchange(String question, String answer) {

    public AssistantExchange {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("an exchange must carry the question that was asked");
        }
    }
}
