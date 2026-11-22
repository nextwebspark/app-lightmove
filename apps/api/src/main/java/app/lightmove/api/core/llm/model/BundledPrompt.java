package app.lightmove.api.core.llm.model;

/** A prompt as this build ships it: {@code prompts/{id}-system.st}, or {@code prompts/{id}.st}. */
public record BundledPrompt(String promptId, String body, String sha256) {
}
