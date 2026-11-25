package app.lightmove.api.core.llm.model;

import app.lightmove.api.core.llm.constant.PromptOrigin;

/** The version of a prompt under the production label, and the newest version a release has shipped for it. */
public record LivePrompt(PromptVersion live, int newestBundledVersion) {

    /** A release shipped a new text after the live edit was made: the edit still serves. */
    public boolean shippedChangedSinceTheEdit() {
        return live.origin() == PromptOrigin.EDITED
                && newestBundledVersion > live.version();
    }
}
