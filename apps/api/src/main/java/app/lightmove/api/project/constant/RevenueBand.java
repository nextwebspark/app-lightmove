package app.lightmove.api.project.constant;

/**
 * The revenue bands a project's company-size scope selects from — a fixed catalog mirroring the distinct
 * {@code revenue_range} values of the company universe ({@code app_lm_companies}). Source of truth, like
 * {@link EmployeeBand}: {@link #value} is the range string verbatim (what a later count query filters
 * {@code revenue_usd} against), and {@link #minUsd}/{@link #maxUsd} carry the bounds in whole US dollars.
 * A {@code null} bound is open-ended — {@code null} lower on the bottom band, {@code null} upper on the top.
 */
public enum RevenueBand {

    R_UNDER_5M("<5M", null, 5_000_000L),
    R_5M_25M("5M-25M", 5_000_000L, 25_000_000L),
    R_25M_100M("25M-100M", 25_000_000L, 100_000_000L),
    R_100M_500M("100M-500M", 100_000_000L, 500_000_000L),
    R_500M_1B("500M-1B", 500_000_000L, 1_000_000_000L),
    R_1B_5B("1B-5B", 1_000_000_000L, 5_000_000_000L),
    R_5B_PLUS("5B+", 5_000_000_000L, null);

    private final String value;
    private final Long minUsd;
    private final Long maxUsd;

    RevenueBand(String value, Long minUsd, Long maxUsd) {
        this.value = value;
        this.minUsd = minUsd;
        this.maxUsd = maxUsd;
    }

    public String value() {
        return value;
    }

    /** Inclusive lower bound in USD, or {@code null} for the open-ended bottom band ({@code <5M}). */
    public Long minUsd() {
        return minUsd;
    }

    /** Exclusive upper bound in USD, or {@code null} for the open-ended top band ({@code 5B+}). */
    public Long maxUsd() {
        return maxUsd;
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
