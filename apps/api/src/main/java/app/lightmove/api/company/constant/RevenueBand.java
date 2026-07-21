package app.lightmove.api.company.constant;

/**
 * The revenue bands a project's company-size scope selects from — a fixed catalog mirroring the distinct
 * {@code revenue_range} values of the company universe ({@code app_lm_companies}). Source of truth, like
 * {@link EmployeeBand}: {@link #value} is the range string verbatim, matched directly against
 * {@code revenue_range} — no numeric bound ever needs computing.
 */
public enum RevenueBand {

    R_UNDER_5M("<5M"),
    R_5M_25M("5M-25M"),
    R_25M_100M("25M-100M"),
    R_100M_500M("100M-500M"),
    R_500M_1B("500M-1B"),
    R_1B_5B("1B-5B"),
    R_5B_PLUS("5B+");

    private final String value;

    RevenueBand(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /** Resolve a wire value ({@code revenue_range} string) to its band, or {@code null} if unknown. */
    public static RevenueBand fromValue(String value) {
        for (RevenueBand band : values()) {
            if (band.value.equals(value)) {
                return band;
            }
        }
        return null;
    }
}
