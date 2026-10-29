package app.lightmove.api.common.time.constant;

/** The clock a workspace or a person reads times in. */
public enum TimeFormatStyle {

    /** 14:30 — the standard. */
    H24("HH:mm"),
    /** 2:30 PM. */
    H12("h:mm a");

    public static final TimeFormatStyle STANDARD = H24;

    private final String pattern;

    TimeFormatStyle(String pattern) {
        this.pattern = pattern;
    }

    /** A {@link java.time.format.DateTimeFormatter} pattern. */
    public String pattern() {
        return pattern;
    }
}
