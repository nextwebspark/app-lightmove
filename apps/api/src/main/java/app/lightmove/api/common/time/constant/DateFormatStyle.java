package app.lightmove.api.common.time.constant;

/** The date shapes a workspace or a person chooses from; a fixed set, never a typed pattern. */
public enum DateFormatStyle {

    DD_MMM_YYYY("dd MMM yyyy"),
    MMM_D_YYYY("MMM d, yyyy"),
    DD_MM_YYYY("dd/MM/yyyy"),
    MM_DD_YYYY("MM/dd/yyyy"),
    YYYY_MM_DD("yyyy-MM-dd");

    public static final DateFormatStyle STANDARD = DD_MMM_YYYY;

    private final String pattern;

    DateFormatStyle(String pattern) {
        this.pattern = pattern;
    }

    public String pattern() {
        return pattern;
    }
}
