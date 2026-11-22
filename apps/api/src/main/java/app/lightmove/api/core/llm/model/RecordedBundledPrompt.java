package app.lightmove.api.core.llm.model;

/** A shipped text the registry already holds, by its hash, and the version it was recorded as. */
public record RecordedBundledPrompt(String promptId, String sha256, int version) {
}
