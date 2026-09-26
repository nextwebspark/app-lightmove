package app.lightmove.api.strategy.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Headcount bands as closed, abutting numeric bounds. A headcount of 0 — Apollo's "unknown", see
 * {@link CompanySortField} — falls in no band, and there is no Unknown band to reach it.
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

    /** Stable across relabelling. */
    private final String value;

    /** Travels in the facets response; never stored. */
    private final String label;

    /** Smallest headcount in the band, inclusive. */
    private final Long lowerBound;

    /** Largest headcount in the band, inclusive, or {@code null} for the open-ended top band. */
    private final Long upperBound;

    public static EmployeeBand fromValue(String value) {
        return ApiValueEnum.fromValue(EmployeeBand.class, value);
    }
}
