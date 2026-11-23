package app.lightmove.api.prompteval.constant;

/** Why a run answers each case as often as it does. */
public enum EvalRepeatsReason {
    /** Once: both versions answer at temperature 0, and the prompt's answers hold still there. */
    ONCE,
    /** A version answers above temperature 0, so one answer says little. */
    TEMPERATURE,
    /** This prompt's answers vary even at temperature 0. */
    PROMPT_VARIES
}
