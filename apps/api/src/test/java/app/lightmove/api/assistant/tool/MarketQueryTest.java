package app.lightmove.api.assistant.tool;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.strategy.model.CompanyScope;
import app.lightmove.api.strategy.model.NumericRange;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** That what the model asked for becomes the scope the market reads, and nothing more. */
class MarketQueryTest {

    @Test
    @DisplayName("a named country and industry land on their own axes")
    void placesTheAxesItWasGiven() {
        CompanyScope scope = MarketQuery.scopeOf(List.of("Saudi Arabia"), List.of("oil & energy"), null, null, null, null);

        assertThat(scope.countries()).containsExactly("Saudi Arabia");
        assertThat(scope.industries()).containsExactly("oil & energy");
    }

    @Test
    @DisplayName("headcount becomes a numeric range, and no band slug is invented for it")
    void readsHeadcountAsANumericRange() {
        CompanyScope scope = MarketQuery.scopeOf(List.of(), List.of(), null, null, 1_000L, 5_000L);

        assertThat(scope.employeeRange()).isEqualTo(new NumericRange(1_000L, 5_000L));
        assertThat(scope.employeeBands())
                .as("the bands are a vocabulary the model was never taught; the range is what it said")
                .isEmpty();
    }

    @Test
    @DisplayName("an omitted axis constrains nothing")
    void leavesAnOmittedAxisOpen() {
        CompanyScope scope = MarketQuery.scopeOf(List.of("Saudi Arabia"), List.of(), null, null, null, null);

        // Empty is CompanyScope's "no constraint on this axis". A blank string reaching a list would
        // read as a constraint nothing satisfies, so the question would come back with zero rows and
        // no sign of why.
        assertThat(scope.industries()).isEmpty();
        assertThat(scope.keywords()).isEmpty();
        assertThat(scope.employeeRange()).isNull();
        assertThat(scope.revenueRange()).isNull();
        assertThat(scope.nameQuery()).isNull();
    }

    @Test
    @DisplayName("a region or a sector lands as every value it was read as")
    void placesEveryResolvedValue() {
        CompanyScope scope = MarketQuery.scopeOf(List.of("Qatar", "Kuwait"), List.of("banking", "insurance"),
                null, null, null, null);

        assertThat(scope.countries()).containsExactly("Qatar", "Kuwait");
        assertThat(scope.industries()).containsExactly("banking", "insurance");
    }
}
