package app.lightmove.api.dashboard.constant;

/** The options a library query may offer; nothing else is accepted on a widget. */
public enum ChartOptionKey {
    /** Which dimension a breakdown counts by. */
    GROUP("group"),
    /** How many of the latest periods a series shows. */
    RANGE("range"),
    /** How many of the largest groups a breakdown keeps, the rest folded into Other. */
    TOP("top");

    private final String key;

    ChartOptionKey(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
