package app.lightmove.api.prompteval.constant;

/** Why a run ended without a result. */
public enum EvalRunFailure {
    /** It had made as many model calls as one run may, repairs among them, with cases still to answer. */
    CALL_CAP_REACHED,
    /** The model gave no answer to a case, asked several times over. */
    MODEL_UNAVAILABLE,
    STOPPED_UNEXPECTEDLY
}
