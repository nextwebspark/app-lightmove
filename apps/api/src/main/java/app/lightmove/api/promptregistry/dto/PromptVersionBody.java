package app.lightmove.api.promptregistry.dto;

/** One version's text, for the editor and for comparing two. */
public record PromptVersionBody(int version, String body) {
}
