package app.lightmove.api.promptregistry.constant;

/**
 * What the evals say about putting a version live in place of the one serving. {@link #NONE}: no finished run on the
 * held-back cases compares the two, so nothing shows the version is no worse.
 */
public enum PromotionGateState {
    PASSED,
    FAILED,
    NONE
}
