package app.lightmove.api.strategy.dto;

import app.lightmove.api.strategy.model.NumericRange;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.PositiveOrZero;

/** A Custom Range, either end optional. An inverted pair is rejected rather than silently swapped. */
public record NumericRangeDto(@PositiveOrZero(message = "min must not be negative") Long min,
                              @PositiveOrZero(message = "max must not be negative") Long max) {

    @AssertTrue(message = "min must not be greater than max")
    public boolean isOrdered() {
        return min == null || max == null || min <= max;
    }

    public static NumericRangeDto of(NumericRange range) {
        return range == null ? null : new NumericRangeDto(range.min(), range.max());
    }
}
