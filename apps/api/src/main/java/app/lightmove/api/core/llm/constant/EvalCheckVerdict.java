package app.lightmove.api.core.llm.constant;

/** What one check found in one answer. {@link #NOT_APPLICABLE}: the case gives the check nothing to judge. */
public enum EvalCheckVerdict {
    PASSED,
    FAILED,
    NOT_APPLICABLE
}
