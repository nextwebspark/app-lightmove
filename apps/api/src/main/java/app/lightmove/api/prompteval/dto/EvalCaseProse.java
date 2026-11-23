package app.lightmove.api.prompteval.dto;

/** A case's two answers as written, for a prompt answered in words: each version's first repeat. */
public record EvalCaseProse(String candidate, String baseline) {
}
