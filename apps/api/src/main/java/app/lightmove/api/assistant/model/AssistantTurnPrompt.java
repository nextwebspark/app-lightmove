package app.lightmove.api.assistant.model;

import java.util.List;

/**
 * Everything one turn is asked with: the operator instructions, what has been said so far, and the
 * new question.
 *
 * <p>The system prompt arrives already composed rather than being built here. What the assistant is
 * told about the caller, the screen and the mandate is its own decision with its own rules — chiefly
 * that the stable part must stay stable, or the cached prefix is lost on every turn — and a runner
 * that assembled it could not be swapped without carrying that decision along.
 */
public record AssistantTurnPrompt(String systemPrompt, List<AssistantExchange> history, String question) {

    public AssistantTurnPrompt {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("a turn must carry a question");
        }
        history = history == null ? List.of() : List.copyOf(history);
    }

    /** The most recent {@code window} exchanges, oldest first, as the model should read them. */
    public AssistantTurnPrompt withHistoryWindow(int window) {
        if (window >= history.size()) {
            return this;
        }
        return new AssistantTurnPrompt(systemPrompt, history.subList(history.size() - window, history.size()),
                question);
    }
}
