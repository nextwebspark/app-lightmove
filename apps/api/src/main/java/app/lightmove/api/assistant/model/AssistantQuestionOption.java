package app.lightmove.api.assistant.model;

/** One choice offered under an {@link AssistantQuestion}: the label to pick and what picking it means. */
public record AssistantQuestionOption(String label, String description) {
}
