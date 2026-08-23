package app.lightmove.api.strategy.dto;

import app.lightmove.api.strategy.model.StrategyFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The Strategy sidebar's whole selection, travelling in both directions: the screen PUTs it as one
 * snapshot and reads it back the same shape.
 *
 * <p>Every list holds wire tokens — Apollo industry values, market-segment names, Apollo country
 * names, and the band slugs from {@code EmployeeBand} / {@code RevenueBand} — never display labels,
 * and never sector group names. A group is expanded to its industries client-side; a market segment
 * is not, because it has no sub-chips to expand into. See {@code StrategyFilter} and
 * {@code MarketSegments}.
 *
 * <p>The size caps are a scope, not an attack: the universe carries 148 industries and six countries,
 * so a request naming hundreds of either is a client bug worth failing loudly rather than a search.
 */
public record StrategyFilterDto(
        @NotNull(message = "industries must be present, even if empty")
        @Size(max = 200, message = "Too many industries selected")
        List<@Size(max = 160) String> industries,

        @NotNull(message = "marketSegments must be present, even if empty")
        @Size(max = 50, message = "Too many market segments selected")
        List<@Size(max = 64) String> marketSegments,

        @NotNull(message = "countries must be present, even if empty")
        @Size(max = 50, message = "Too many countries selected")
        List<@Size(max = 100) String> countries,

        @NotNull(message = "employeeBands must be present, even if empty")
        @Size(max = 20, message = "Too many employee bands selected")
        List<@Size(max = 32) String> employeeBands,

        @NotNull(message = "revenueBands must be present, even if empty")
        @Size(max = 20, message = "Too many revenue bands selected")
        List<@Size(max = 32) String> revenueBands,

        /*
         * Custom Range mode, one per numeric axis, null when the predefined rows are in force. Both
         * are @Valid so the range's own bounds check runs — validation on a nested record is opt-in,
         * and without this annotation a negative or inverted range would reach the query builder.
         */
        @Valid NumericRangeDto employeeRange,

        @Valid NumericRangeDto revenueRange
) {

    public static StrategyFilterDto of(StrategyFilter filter) {
        return new StrategyFilterDto(filter.industries(), filter.marketSegments(), filter.countries(),
                filter.employeeBands(), filter.revenueBands(),
                NumericRangeDto.of(filter.employeeRange()), NumericRangeDto.of(filter.revenueRange()));
    }
}
