package app.lightmove.api.strategy.constant;

/**
 * The headcount bands the Strategy filter selects from, matching the wireframe's rows exactly.
 *
 * <p>These are <b>numeric bounds, not range strings</b>. The brightdata warehouse shipped a
 * pre-bucketed {@code employee_range} column a band could be compared to directly; Apollo ships only
 * a raw {@code num_employees} integer, so every band states the range it means and the query builder
 * turns it into a BETWEEN. The bounds are closed on both ends and abut without overlapping — 20 is
 * the top of {@code B_11_20}, 21 the bottom of {@code B_21_50} — so a company falls in exactly one
 * band.
 *
 * <p><b>The cut is deliberately fine at the bottom.</b> Eleven bands rather than a handful, and five
 * of them under 500 people, because that is where the universe actually sits: a coarse "1-50" bucket
 * would put over a third of the market behind one row and make the filter useless for exactly the
 * searches that need it most. The wireframe cuts it this way and the data agrees.
 *
 * <p>{@link #value} is a slug, not the label. The label is presentation and will change; the slug is
 * what a saved search stores, and a stored filter that stops resolving because someone retitled a
 * row is a silent scope change on a live mandate. The label travels beside the slug in the facets
 * response, so the client never mirrors it.
 *
 * <p>Every row in {@code app_lm_apollo_companies} carries a headcount — the column is 100% populated
 * across all 71,822 rows — so unlike {@link RevenueBand} this axis needs no Unknown band.
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
