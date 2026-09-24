package app.lightmove.api.assistant.model;

/** One thing the assistant did while answering, e.g. a search and how many companies it matched. */
public record AssistantStep(String label, String detail) {
}
