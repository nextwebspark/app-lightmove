package app.lightmove.api.core.llm.model;

/** How many of a version's answers a person kept as written, and how many they changed. */
public record PromptVersionOutcomes(int version, long accepted, long changed) {
}
