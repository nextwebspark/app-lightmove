package app.uncava.api.assistant.model;

/** A step as the panel receives it while the answer is still being worked out. */
public record AssistantStepEvent(int index, String label, String detail, boolean done) {
}
