package app.lightmove.api.core.llm.constant;

/** How a prompt's answer is read, which decides the answer format the provider is asked for. */
public enum PromptKind {
    /** Bound to a record through a JSON schema. */
    STRUCTURED(ModelCapability.STRUCTURED_ANSWER),
    PROSE(null),
    TOOL_CALLING(ModelCapability.TOOL_CALLING);

    private final ModelCapability needs;

    PromptKind(ModelCapability needs) {
        this.needs = needs;
    }

    /** The capability a model must have to answer this kind, or null when any model will do. */
    public ModelCapability needs() {
        return needs;
    }
}
