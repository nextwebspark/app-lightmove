package app.lightmove.api.strategy.constant;

/**
 * The revenue bands the Strategy filter selects from — the wireframe's ten rows, plus one it could
 * not know it needed.
 *
 * <p>Like {@link EmployeeBand} these are numeric USD bounds rather than range strings, because Apollo
 * ships a raw {@code annual_revenue} figure and no pre-bucketed column. The bounds are closed on both
 * ends and abut without overlapping, so a company falls in exactly one band.
 *
 * <p><b>{@code R_UNKNOWN} is the one addition, and the live data is why.</b> Apollo carries a revenue
 * figure on 7,132 of 71,822 rows — 9.9%. Ship the wireframe's ten bands alone and selecting any one
 * of them silently drops nine companies in ten: the screen would look like the market is tiny rather
 * than like the data is thin, and nothing on the panel would say which. {@code R_UNKNOWN} makes those
 * 64,690 rows selectable and countable, so the gap is something a consultant can see and decide
 * about rather than something that quietly eats their search.
 *
 * <p>It carries no bounds at all — {@link #lowerBound()} and {@link #upperBound()} are both null —
 * and the query builder renders it as {@code annual_revenue IS NULL}. Any caller reading bounds must
 * check {@link #isUnknown()} first.
 */
public enum RevenueBand {

    R_UNDER_1M("under-1m", "< $1M", 0L, 999_999L),
    R_1M_10M("1m-10m", "$1M - $10M", 1_000_000L, 9_999_999L),
    R_10M_50M("10m-50m", "$10M - $50M", 10_000_000L, 49_999_999L),
    R_50M_100M("50m-100m", "$50M - $100M", 50_000_000L, 99_999_999L),
    R_100M_200M("100m-200m", "$100M - $200M", 100_000_000L, 199_999_999L),
    R_200M_500M("200m-500m", "$200M - $500M", 200_000_000L, 499_999_999L),
    R_500M_1B("500m-1b", "$500M - $1B", 500_000_000L, 999_999_999L),
    R_1B_5B("1b-5b", "$1B - $5B", 1_000_000_000L, 4_999_999_999L),
    R_5B_10B("5b-10b", "$5B - $10B", 5_000_000_000L, 9_999_999_999L),
    R_10B_PLUS("10b-plus", "$10B+", 10_000_000_000L, null),
    R_UNKNOWN("unknown", "Unknown", null, null);

    private final String value;
    private final String label;
    private final Long lowerBound;
    private final Long upperBound;

    RevenueBand(String value, String label, Long lowerBound, Long upperBound) {
        this.value = value;
        this.label = label;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }

    /** The wire token a filter stores and a request names. Stable across relabelling. */
    public String value() {
        return value;
    }

    /** What the row reads. Travels in the facets response; never stored. */
    public String label() {
        return label;
    }

    /** The band that means "no figure published", which is most of the universe. */
    public boolean isUnknown() {
        return this == R_UNKNOWN;
    }

    /** Smallest revenue in the band in USD, inclusive, or {@code null} when {@link #isUnknown()}. */
    public Long lowerBound() {
        return lowerBound;
    }

    /** Largest revenue in USD, inclusive; {@code null} for the open-ended top band and for Unknown. */
    public Long upperBound() {
        return upperBound;
    }

    /** Resolve a wire token to its band, or {@code null} if unknown. */
    public static RevenueBand fromValue(String value) {
        for (RevenueBand band : values()) {
            if (band.value.equals(value)) {
                return band;
            }
        }
        return null;
    }
}
