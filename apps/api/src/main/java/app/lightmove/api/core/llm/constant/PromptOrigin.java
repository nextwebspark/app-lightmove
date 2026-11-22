package app.lightmove.api.core.llm.constant;

/** Where a prompt version's text came from: a release's {@code prompts/*.st}, or the platform console. */
public enum PromptOrigin {
    BUNDLED,
    EDITED
}
