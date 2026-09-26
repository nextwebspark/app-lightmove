package app.lightmove.api.strategy.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Revenue bands in USD. {@code R_UNKNOWN} is added to the wireframe's ten because only about one row in
 * ten carries a figure; it has no bounds, so check {@link #isUnknown()} before reading them.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum RevenueBand implements CompanySizeBand {

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

    /** Stable across relabelling. */
    private final String value;

    /** Travels in the facets response; never stored. */
    private final String label;

    /** USD, inclusive; {@code null} when {@link #isUnknown()}. */
    private final Long lowerBound;

    /** Inclusive; {@code null} for the open-ended top band and for Unknown. */
    private final Long upperBound;

    /** Most of the universe publishes no figure. */
    @Override
    public boolean isUnknown() {
        return this == R_UNKNOWN;
    }

    public static RevenueBand fromValue(String value) {
        return ApiValueEnum.fromValue(RevenueBand.class, value);
    }
}
