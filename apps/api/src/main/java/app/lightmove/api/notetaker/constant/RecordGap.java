package app.lightmove.api.notetaker.constant;

/**
 * A fact the person's record is missing that a call is the natural place to learn. The model is told only which are
 * missing, never what any held one says, and never a package figure.
 */
public enum RecordGap {
    NOTICE,
    PACKAGE,
    LOCATION,
    RELOCATION,
    MOTIVATION,
    EXPECTED_PACKAGE
}
