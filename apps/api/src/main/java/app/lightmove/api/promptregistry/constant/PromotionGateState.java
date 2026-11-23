package app.lightmove.api.promptregistry.constant;

/**
 * What the evals say about putting a version live in place of the one serving. {@link #NONE}: no finished run on the
 * held-back cases compares the two, so nothing shows the version is no worse.
 */
public enum PromotionGateState {
    PASSED,
    FAILED,
    /** A run finished and nothing it measured counts: a prompt only a judge scores, whose rubric does not count. */
    NO_RESULT,
    NONE
}
