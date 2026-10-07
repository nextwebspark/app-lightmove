package app.lightmove.api.assistant.tool;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.strategy.model.CompanyExclusion;
import app.lightmove.api.strategy.model.CompanyScope;
import app.lightmove.api.strategy.model.NumericRange;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A refinement's question restates the whole search, and its count leaves out what the mandate holds. */
class MarketAskTest {

    private final MarketAsk retailInUae = new MarketAsk(List.of("United Arab Emirates"), List.of("retail"), null,
            null, null);

    @Test
    @DisplayName("the question carries every axis, so the model infers nothing from the chat")
    void restatesTheWholeSearch() {
        assertThat(retailInUae.withIndustry("hospitality").withCountry("Saudi Arabia").withMinEmployees(1_000)
                .asQuestion())
                .isEqualTo("Find retail and hospitality companies in United Arab Emirates and Saudi Arabia "
                        + "with at least 1,000 staff");
        assertThat(new MarketAsk(List.of(), List.of(), "grocery", null, 500L).asQuestion())
                .isEqualTo("Find companies describing themselves as \"grocery\" with up to 500 staff");
    }

    @Test
    @DisplayName("each change touches one axis and leaves the others as they were")
    void changesOneAxis() {
        MarketAsk wide = new MarketAsk(List.of("Qatar", "Oman"), List.of("retail", "hospitality"), "luxury",
                250L, 5_000L);

        assertThat(wide.onlyIn("Oman").countries()).containsExactly("Oman");
        assertThat(wide.withoutIndustry("Retail").industries()).containsExactly("hospitality");
        assertThat(wide.withoutKeyword().keyword()).isNull();
        assertThat(wide.ofAnySize()).extracting(MarketAsk::minEmployees, MarketAsk::maxEmployees)
                .containsExactly(null, null);
        assertThat(wide.withIndustry("retail").industries()).containsExactly("retail", "hospitality");
    }

    @Test
    @DisplayName("the counted scope excludes the mandate's own companies and its off-limits ones")
    void countsOnlyNewCompanies() {
        CompanyExclusion filed = new CompanyExclusion("NOT EXISTS (SELECT 1)", Map.of());

        CompanyScope scope = retailInUae.withMinEmployees(1_000).newCompaniesScope(List.of("off1"), filed);

        assertThat(scope.triagedExclusion()).isSameAs(filed);
        assertThat(scope.offLimitsAccountIds()).containsExactly("off1");
        assertThat(scope.employeeRange()).isEqualTo(new NumericRange(1_000L, null));
        assertThat(scope.nameQuery()).isNull();
    }
}
