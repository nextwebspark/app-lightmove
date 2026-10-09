package app.lightmove.api.assistant.model;

import java.util.List;

/** One clarifying question the assistant put to the consultant, with the choices it offered. */
public record AssistantQuestion(String question, String header, List<AssistantQuestionOption> options,
                                boolean multiSelect) {
}
