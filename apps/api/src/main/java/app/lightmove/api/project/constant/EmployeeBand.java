package app.lightmove.api.project.constant;

/**
 * The headcount bands a project's company-size scope selects from — a fixed catalog mirroring the
 * distinct {@code employee_range} values of the company universe ({@code app_lm_companies}). This enum
 * is the source of truth: {@link #value} is that range string verbatim (what a later count query filters
 * {@code employee_count} against), and {@link #minCount}/{@link #maxCount} carry the inclusive bounds so
 * that query drops in without re-deriving them. A {@code null} upper bound is the open-ended top band.
 *
 * <p>The frontend keeps a mirror of the same values for instant rendering; a drift test on each side
 * keeps the two in step.
 */
public enum EmployeeBand {

    B_1_10("1-10", 1, 10),
    B_11_50("11-50", 11, 50),
    B_51_200("51-200", 51, 200),
    B_201_500("201-500", 201, 500),
    B_501_1000("501-1000", 501, 1000),
    B_1001_5000("1001-5000", 1001, 5000),
    B_5001_10000("5001-10000", 5001, 10000),
    B_10000_PLUS("10000+", 10000, null);

    private final String value;
    private final int minCount;
    private final Integer maxCount;

    EmployeeBand(String value, int minCount, Integer maxCount) {
        this.value = value;
        this.minCount = minCount;
        this.maxCount = maxCount;
    }

    public String value() {
        return value;
    }

    public int minCount() {
        return minCount;
    }

    /** Inclusive upper bound, or {@code null} for the open-ended top band ({@code 10000+}). */
    public Integer maxCount() {
        return maxCount;
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
