package app.lightmove.api.assistant.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.strategy.constant.CompanySortField;
import app.lightmove.api.strategy.constant.SortDirection;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.strategy.model.CompanyScope;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** That a capped page still tells the model how much of the market it is looking at. */
class MarketSearchTest {

    private static final int CAP = 25;

    private final ApolloCompanyQueryService companies = mock(ApolloCompanyQueryService.class);

    @Test
    @DisplayName("the total is the market's, not the page's")
    void reportsTheTotalBesideThePage() {
        MarketSearch search = searchOver(1_284L, rows(CAP));

        CompanyMatches matches = search.matching(CompanyScope.unfiltered());

        // The mockup's own trace line is "1,284 matched". A model told only about the 25 rows in
        // front of it totals them and calls them the market.
        assertThat(matches.matched()).isEqualTo(1_284L);
        assertThat(matches.showing()).isEqualTo(CAP);
        assertThat(matches.companies()).hasSize(CAP);
    }

    @Test
    @DisplayName("a search that fits reports the same number twice")
    void showsEverythingWhenItFits() {
        MarketSearch search = searchOver(3L, rows(3));

        CompanyMatches matches = search.matching(CompanyScope.unfiltered());

        assertThat(matches.matched()).isEqualTo(3L);
        assertThat(matches.showing()).isEqualTo(3);
    }

    @Test
    @DisplayName("the page is the first of the biggest, capped at the assistant's own row limit")
    void asksForTheBiggestFirstPageOnly() {
        MarketSearch search = searchOver(100L, rows(CAP));
        CompanyScope scope = CompanyScope.unfiltered();

        search.matching(scope);

        verify(companies).search(eq(scope), eq(CompanySortField.EMPLOYEES),
                eq(SortDirection.DESC), eq(0), eq(CAP));
    }

    private MarketSearch searchOver(long matched, List<CompanyRow> page) {
        when(companies.count(any())).thenReturn(matched);
        when(companies.search(any(), any(), any(), anyInt(), anyInt())).thenReturn(page);
        LightMoveProperties properties = mock(LightMoveProperties.class, RETURNS_DEEP_STUBS);
        when(properties.assistant().toolRowLimit()).thenReturn(CAP);
        return new MarketSearch(companies, properties);
    }

    private static List<CompanyRow> rows(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> new CompanyRow("acct-" + index, "Company " + index, "oil & energy",
                        "Saudi Arabia", "Riyadh", 1_000 - index, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null, null,
                        List.of(), List.of(), List.of(), List.of()))
                .toList();
    }
}
