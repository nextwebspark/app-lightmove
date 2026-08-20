package app.lightmove.api.strategy.dto;

import app.lightmove.api.strategy.model.NumericRange;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * The Custom Range inputs on the Employees and Revenue panels: a headcount or a USD figure, either
 * end optional.
 *
 * <p>Both ends optional is the point. The consultant types one box at a time, and a filter that
 * refused to narrow until both were filled would fight the way the panel is used — "at least 500"
 * has to be a legal thing to ask for on its own.
 *
 * <p>An inverted pair is rejected rather than silently swapped. Min 5000 / max 500 is a typo, and
 * quietly reinterpreting it would return a page of companies the consultant did not ask for while
 * the inputs on screen said something else.
 */
public record NumericRangeDto(@PositiveOrZero(message = "min must not be negative") Long min,
                              @PositiveOrZero(message = "max must not be negative") Long max) {

    @AssertTrue(message = "min must not be greater than max")
    public boolean isOrdered() {
        return min == null || max == null || min <= max;
    }

    /** Null in, null out: an axis in predefined mode has no range to describe. */
    public static NumericRangeDto of(NumericRange range) {
        return range == null ? null : new NumericRangeDto(range.min(), range.max());
    }
}
