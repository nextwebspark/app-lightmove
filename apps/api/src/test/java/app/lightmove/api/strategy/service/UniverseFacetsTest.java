package app.lightmove.api.strategy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.lightmove.api.strategy.dto.FacetCount;
import app.lightmove.api.strategy.model.ScopeBreakdown;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** That the whole-universe counts are read once per TTL rather than once per caller. */
class UniverseFacetsTest {

    private static final Duration TTL = Duration.ofHours(1);

    private final ApolloCompanyQueryService companies = mock(ApolloCompanyQueryService.class);
    private final AtomicLong nanos = new AtomicLong();
    private final UniverseFacets facets = new UniverseFacets(companies, TTL, nanos::get);

    @Test
    @DisplayName("a second read within the TTL does not reach the database")
    void answersRepeatReadsFromMemory() {
        when(companies.marketSegmentFacets()).thenReturn(List.of(new FacetCount("B2B", "B2B", 7)));

        facets.marketSegments();
        List<FacetCount> again = facets.marketSegments();

        assertThat(again).extracting(FacetCount::count).containsExactly(7L);
        verify(companies, times(1)).marketSegmentFacets();
    }

    @Test
    @DisplayName("a read after the TTL counts the universe again")
    void recountsAfterTheTtl() {
        when(companies.sectorGroups()).thenReturn(List.of());

        facets.sectorGroups();
        nanos.addAndGet(TTL.plusSeconds(1).toNanos());
        facets.sectorGroups();

        verify(companies, times(2)).sectorGroups();
    }

    @Test
    @DisplayName("countries asked for at two limits are two answers, not one")
    void keysCountriesByLimit() {
        when(companies.countByCountry(any(), eq(8)))
                .thenReturn(List.of(new ScopeBreakdown("Saudi Arabia", 5)));
        when(companies.countByCountry(any(), eq(250)))
                .thenReturn(List.of(new ScopeBreakdown("Saudi Arabia", 5), new ScopeBreakdown("Oman", 1)));

        assertThat(facets.countries(8)).hasSize(1);
        assertThat(facets.countries(250)).hasSize(2);
    }
}
