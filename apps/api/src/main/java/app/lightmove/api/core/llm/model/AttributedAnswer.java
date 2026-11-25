package app.lightmove.api.core.llm.model;

/** A bound answer with the version that gave it; {@code answeredBy} null when the bundled file or a trial answered. */
public record AttributedAnswer<T>(T answer, PromptVersionRef answeredBy) {
}
