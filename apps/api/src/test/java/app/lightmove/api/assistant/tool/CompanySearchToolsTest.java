package app.lightmove.api.assistant.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.lightmove.api.assistant.model.AssistantStepEvent;
import app.lightmove.api.strategy.service.IndustryAdjacency;
import app.lightmove.api.strategy.service.SectorTaxonomy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import tools.jackson.databind.ObjectMapper;

/** What a search tells the person waiting: what it looked for, then how much it found. */
class CompanySearchToolsTest {

    private final MarketSearch market = mock(MarketSearch.class);
    private final IndustryAdjacency adjacency = mock(IndustryAdjacency.class);
    private final List<AssistantStepEvent> sent = new ArrayList<>();
    private final TurnRecorder recorder = new TurnRecorder(sent::add);

    @Test
    @DisplayName("a search is announced as it starts and finished with its count")
    void reportsTheSearchAsAStep() {
        when(market.matching(any())).thenReturn(new CompanyMatches(342, 25, List.of(), List.of()));

        tools().searchCompanyUniverse("United Arab Emirates", "retail", null,
                null, null, null, context());

        assertThat(sent).extracting(AssistantStepEvent::done).containsExactly(false, true);
        assertThat(recorder.steps()).singleElement().satisfies(step -> {
            assertThat(step.label()).isEqualTo("Searching retail companies in United Arab Emirates");
            assertThat(step.detail()).isEqualTo("342 matched, showing the top 25");
        });
    }

    @Test
    @DisplayName("a search for an industry carries the industries beside it, and remembers what it found")
    void carriesTheAdjacentIndustries() {
        when(market.matching(any())).thenReturn(new CompanyMatches(2, 2, List.of(
                new MarketCompanySummary("a1", "Lulu Retail", "retail", "United Arab Emirates", "Abu Dhabi",
                        55_000, 2000, null)), List.of()));
        when(adjacency.neighboursOf("retail")).thenReturn(List.of("apparel & fashion", "consumer goods"));

        CompanyMatches matches = tools().searchCompanyUniverse(
                "United Arab Emirates", "retail", null, null, null, null, context());

        assertThat(matches.adjacentIndustries()).containsExactly("apparel & fashion", "consumer goods");
        assertThat(recorder.foundAccountIds()).containsExactly("a1");
    }

    @Test
    @DisplayName("an empty search says so rather than reporting zero of zero")
    void saysWhenNothingMatched() {
        assertThat(CompanySearchTools.describeMatches(new CompanyMatches(0, 0, List.of(), List.of())))
                .isEqualTo("No companies matched");
        assertThat(CompanySearchTools.describeMatches(new CompanyMatches(12, 12, List.of(), List.of())))
                .isEqualTo("12 matched");
    }

    @Test
    @DisplayName("the label reads only the constraints the search was given")
    void describesOnlyWhatWasAsked() {
        assertThat(CompanySearchTools.describeSearch("Qatar", null, null, null, null, null))
                .isEqualTo("Searching companies in Qatar");
        assertThat(CompanySearchTools.describeSearch("Qatar", "construction", null, null, 500L, 5_000L))
                .isEqualTo("Searching construction companies in Qatar with 500–5,000 staff");
        assertThat(CompanySearchTools.describeSearch(null, null, "solar", "ACWA", 1_000L, null))
                .isEqualTo("Searching \"solar\" companies named ACWA with at least 1,000 staff");
    }

    @Test
    @DisplayName("a plain country spelling is searched and labelled as the universe spells it")
    void readsAPlainCountrySpelling() {
        when(market.matching(any())).thenReturn(new CompanyMatches(10, 10, List.of(), List.of()));

        tools().searchCompanyUniverse("UAE", "Retail", null, null, null, null, context());

        verify(market).matching(argThat(scope -> scope.countries().equals(List.of("United Arab Emirates"))
                && scope.industries().equals(List.of("retail"))));
        assertThat(recorder.steps().getFirst().label())
                .isEqualTo("Searching retail companies in United Arab Emirates");
    }

    @Test
    @DisplayName("a region is searched as its countries, and the answer says how it was read")
    void searchesARegionAsItsCountries() {
        when(market.matching(any())).thenReturn(new CompanyMatches(40, 25, List.of(), List.of()));

        CompanyMatches matches = tools().searchCompanyUniverse("GCC", "banking", null, null, null, null,
                context());

        verify(market).matching(argThat(scope -> scope.countries().size() == 6));
        assertThat(matches.interpretedAs()).singleElement().asString().startsWith("GCC → ");
        assertThat(recorder.steps().getFirst().label()).isEqualTo("Searching banking companies in GCC");
    }

    @Test
    @DisplayName("a spelling nothing can read is reported, and nothing is searched without it")
    void refusesToSearchWithoutAnUnreadConstraint() {
        CompanyMatches matches = tools().searchCompanyUniverse("Levantia", "retail", null, null, null, null,
                context());

        verifyNoInteractions(market);
        assertThat(matches.unrecognised()).containsExactly("country \"Levantia\"");
        assertThat(matches.note()).contains("describeMarket");
        assertThat(recorder.steps().getFirst().detail()).isEqualTo("Couldn't read country \"Levantia\"");
    }

    private CompanySearchTools tools() {
        return new CompanySearchTools(market, adjacency, new MarketTerms(new SectorTaxonomy(new ObjectMapper())));
    }

    private ToolContext context() {
        return new ToolContext(new AssistantToolContext(UUID.randomUUID(), UUID.randomUUID(), recorder).asMap());
    }
}
