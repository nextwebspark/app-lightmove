package app.lightmove.api.notetaker.constant;

/** How far a call has shown a competency: not yet, mentioned with no example, or evidenced. Only ever moves forward. */
public enum CoverageState {
    NOT_YET,
    TOUCHED,
    EVIDENCED;

    public CoverageState atLeast(CoverageState other) {
        return other != null && other.ordinal() > ordinal() ? other : this;
    }
}
