package app.lightmove.api.strategy.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

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
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum EmployeeBand implements CompanySizeBand {

    B_1_10("1-10", "1-10", 1L, 10L),
    B_11_20("11-20", "11-20", 11L, 20L),
    B_21_50("21-50", "21-50", 21L, 50L),
    B_51_100("51-100", "51-100", 51L, 100L),
    B_101_200("101-200", "101-200", 101L, 200L),
    B_201_500("201-500", "201-500", 201L, 500L),
    B_501_1000("501-1000", "501-1000", 501L, 1_000L),
    B_1001_2000("1001-2000", "1001-2000", 1_001L, 2_000L),
    B_2001_5000("2001-5000", "2001-5000", 2_001L, 5_000L),
    B_5001_10000("5001-10000", "5001-10000", 5_001L, 10_000L),
    B_10000_PLUS("10000-plus", "10001+", 10_001L, null);

    /** The wire token a filter stores and a request names. Stable across relabelling. */
    private final String value;

    /** What the row reads. Travels in the facets response; never stored. */
    private final String label;

    /** Smallest headcount in the band, inclusive. */
    private final Long lowerBound;

    /** Largest headcount in the band, inclusive, or {@code null} for the open-ended top band. */
    private final Long upperBound;

    /** Resolve a wire token to its band, or {@code null} if unknown. */
    public static EmployeeBand fromValue(String value) {
        return ApiValueEnum.fromValue(EmployeeBand.class, value);
    }
}
