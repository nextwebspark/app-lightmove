package app.lightmove.api.common.time.constant;

/** The date shapes a workspace or a person chooses from; a fixed set, never a typed pattern. */
public enum DateFormatStyle {

    /** 04 Dec 2026 — the standard: unambiguous in every country. */
    DD_MMM_YYYY("dd MMM yyyy"),
    /** Dec 4, 2026. */
    MMM_D_YYYY("MMM d, yyyy"),
    /** 04/12/2026. */
    DD_MM_YYYY("dd/MM/yyyy"),
    /** 12/04/2026. */
    MM_DD_YYYY("MM/dd/yyyy"),
    /** 2026-12-04, ISO 8601. */
    YYYY_MM_DD("yyyy-MM-dd");

    public static final DateFormatStyle STANDARD = DD_MMM_YYYY;

    private final String pattern;

    DateFormatStyle(String pattern) {
        this.pattern = pattern;
    }

    /** A {@link java.time.format.DateTimeFormatter} pattern. */
    public String pattern() {
        return pattern;
    }
}
