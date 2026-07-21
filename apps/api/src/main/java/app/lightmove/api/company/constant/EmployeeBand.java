package app.lightmove.api.company.constant;

/**
 * The headcount bands a project's company-size scope selects from — a fixed catalog mirroring the
 * distinct {@code employee_range} values of the company universe ({@code app_lm_companies}). This enum
 * is the source of truth: {@link #value} is that range string verbatim, matched directly against
 * {@code employee_range} — the catalog values already agree with the warehouse's own strings, so no
 * numeric bound ever needs computing.
 *
 * <p>The frontend keeps a mirror of the same values for instant rendering; a drift test on each side
 * keeps the two in step.
 */
public enum EmployeeBand {

    B_1_10("1-10"),
    B_11_50("11-50"),
    B_51_200("51-200"),
    B_201_500("201-500"),
    B_501_1000("501-1000"),
    B_1001_5000("1001-5000"),
    B_5001_10000("5001-10000"),
    B_10000_PLUS("10000+");

    private final String value;

    EmployeeBand(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /** Resolve a wire value ({@code employee_range} string) to its band, or {@code null} if unknown. */
    public static EmployeeBand fromValue(String value) {
        for (EmployeeBand band : values()) {
            if (band.value.equals(value)) {
                return band;
            }
        }
        return null;
    }
}
