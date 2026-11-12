package app.lightmove.api.common.time.constant;

/** The clock a workspace or a person reads times in. */
public enum TimeFormatStyle {

    H24("HH:mm"),
    H12("h:mm a");

    public static final TimeFormatStyle STANDARD = H24;

    private final String pattern;

    TimeFormatStyle(String pattern) {
        this.pattern = pattern;
    }

    public String pattern() {
        return pattern;
    }
}
