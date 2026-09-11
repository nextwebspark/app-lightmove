package app.lightmove.api.strategy.constant;

/**
 * The headcount bands the Strategy filter selects from.
 *
 * <p>These are <b>numeric bounds, not range strings</b>: Apollo ships a raw {@code num_employees}
 * integer and no pre-bucketed column, so every band states the range it means and the query builder
 * turns it into a BETWEEN. The bounds are closed on both ends and abut without overlapping.
 *
 * <p>{@link #value} is a slug, not the label — see {@link app.lightmove.api.strategy.model.StrategyFilter}
 * for why a stored filter never holds a label.
 *
 * <p><b>A headcount of 0 falls in no band</b>, because the lowest starts at 1 and there is no Unknown
 * band as {@link RevenueBand} has. Apollo encodes "we don't know" as a zero on this column —
 * {@code CompanySortField} wraps it in {@code NULLIF} for exactly that reason — so those companies are
 * unreachable through the Employees panel even with every band selected.
 * {@code docs/strategy-company-search-uat.md} measured it.
 */
public enum EmployeeBand {

    B_1_10("1-10", "1-10", 1, 10L),
    B_11_20("11-20", "11-20", 11, 20L),
    B_21_50("21-50", "21-50", 21, 50L),
    B_51_100("51-100", "51-100", 51, 100L),
    B_101_200("101-200", "101-200", 101, 200L),
    B_201_500("201-500", "201-500", 201, 500L),
    B_501_1000("501-1000", "501-1000", 501, 1_000L),
    B_1001_2000("1001-2000", "1001-2000", 1_001, 2_000L),
    B_2001_5000("2001-5000", "2001-5000", 2_001, 5_000L),
    B_5001_10000("5001-10000", "5001-10000", 5_001, 10_000L),
    B_10000_PLUS("10000-plus", "10001+", 10_001, null);

    private final String value;
    private final String label;
    private final long lowerBound;
    private final Long upperBound;

    EmployeeBand(String value, String label, long lowerBound, Long upperBound) {
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

    /** Smallest headcount in the band, inclusive. */
    public long lowerBound() {
        return lowerBound;
    }

    /** Largest headcount in the band, inclusive, or {@code null} for the open-ended top band. */
    public Long upperBound() {
        return upperBound;
    }

    /** Resolve a wire token to its band, or {@code null} if unknown. */
    public static EmployeeBand fromValue(String value) {
        for (EmployeeBand band : values()) {
            if (band.value.equals(value)) {
                return band;
            }
        }
        return null;
    }
}
