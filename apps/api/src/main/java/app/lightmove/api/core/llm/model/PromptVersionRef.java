package app.lightmove.api.core.llm.model;

/**
 * Which registry version of a prompt produced an answer: what a feature keeps beside an AI value so the person's
 * decision on it can be counted against that version. Never any content.
 */
public record PromptVersionRef(String promptId, int version) {
}
