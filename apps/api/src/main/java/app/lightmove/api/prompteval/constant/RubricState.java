package app.lightmove.api.prompteval.constant;

/**
 * Whether a rubric's judged criteria count toward a run's result. Only {@link #COUNTS} does; in every other state
 * they are judged and shown, and change no result.
 */
public enum RubricState {
    /** The judge agrees with a person's labels on enough of the picks. */
    COUNTS,
    /** It was checked and agrees on too few. */
    SHOWN_ONLY,
    /** A person has not labelled every pair yet. */
    UNLABELLED,
    /** Every pair is labelled and the judge has not been checked against them. */
    UNCHECKED,
    /** The judge's live version, or a label, changed since the last check. */
    STALE,
    /** A check is going. */
    CHECKING
}
