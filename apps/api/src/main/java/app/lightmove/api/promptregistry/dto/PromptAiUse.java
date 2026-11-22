package app.lightmove.api.promptregistry.dto;

/** What a prompt leaves on a person, as the subject-access text describes it: a change to it needs that text updated. */
public record PromptAiUse(String title, String logic) {
}
