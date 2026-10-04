package app.lightmove.api.strategy.dto;

import app.lightmove.api.strategy.model.StrategyFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The Strategy sidebar's whole selection, both directions. Lists hold wire tokens only; the size caps
 * catch client bugs, since the universe has 148 industries.
 */
public record StrategyFilterDto(
        @NotNull(message = "industries must be present, even if empty")
        @Size(max = 200, message = "Too many industries selected")
        List<@Size(max = 160) String> industries,

        @NotNull(message = "keywords must be present, even if empty")
        @Size(max = 50, message = "Too many keywords selected")
        List<@Size(max = 64) String> keywords,

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

        // @Valid so the nested range's bounds check runs; nested validation is opt-in.
        @Valid NumericRangeDto employeeRange,

        @Valid NumericRangeDto revenueRange
) {

    public static StrategyFilterDto of(StrategyFilter filter) {
        return new StrategyFilterDto(filter.industries(), filter.keywords(), filter.marketSegments(),
                filter.countries(), filter.employeeBands(), filter.revenueBands(),
                NumericRangeDto.of(filter.employeeRange()), NumericRangeDto.of(filter.revenueRange()));
    }
}
