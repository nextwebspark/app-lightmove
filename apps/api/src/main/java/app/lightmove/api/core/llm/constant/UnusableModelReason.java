package app.lightmove.api.core.llm.constant;

/** Why a prompt version's model cannot answer it, so the configured model would answer instead. */
public enum UnusableModelReason {
    NOT_IN_CATALOGUE,
    RETIRED,
    LACKS_CAPABILITY
}
