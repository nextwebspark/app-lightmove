package app.lightmove.api.assistant.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.lightmove.api.assistant.model.AssistantStep;
import app.lightmove.api.assistant.model.AssistantStepEvent;
import app.lightmove.api.strategy.service.IndustryAdjacency;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.model.MandateStages;
import app.lightmove.api.triagecompany.service.TriageCompanyReadService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

/** What a search tells the person waiting: what it looked for, then how much it found. */
class CompanySearchToolsTest {

    private final MarketSearch market = mock(MarketSearch.class);
    private final IndustryAdjacency adjacency = mock(IndustryAdjacency.class);
    private final TriageCompanyReadService triaged = mock(TriageCompanyReadService.class);
    private final List<AssistantStepEvent> sent = new ArrayList<>();
    private final TurnRecorder recorder = new TurnRecorder(sent::add);

    @Test
    @DisplayName("a search is announced as it starts and finished with its count")
    void reportsTheSearchAsAStep() {
        when(market.matching(any())).thenReturn(new CompanyMatches(342, 25, List.of(), List.of(), List.of()));

        tools().searchCompanyUniverse(List.of("United Arab Emirates"), List.of("retail"), null,
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
                        55_000, 2000, null, null)), List.of(), List.of()));
        when(adjacency.neighboursOf("retail")).thenReturn(List.of("apparel & fashion", "consumer goods"));

        CompanyMatches matches = tools().searchCompanyUniverse(
                List.of("United Arab Emirates"), List.of("retail"), null, null, null, null, context());

        assertThat(matches.adjacentIndustries()).containsExactly("apparel & fashion", "consumer goods");
        assertThat(recorder.foundAccountIds()).containsExactly("a1");
    }

    @Test
    @DisplayName("several industries and countries are one search, with every neighbour the search does not cover")
    void searchesSeveralAxesAtOnce() {
        when(market.matching(any())).thenReturn(new CompanyMatches(40, 25, List.of(), List.of(), List.of()));
        when(adjacency.neighboursOf("retail")).thenReturn(List.of("hospitality", "consumer goods"));
        when(adjacency.neighboursOf("hospitality")).thenReturn(List.of("retail", "leisure", "consumer goods"));

        CompanyMatches matches = tools().searchCompanyUniverse(List.of("United Arab Emirates", " Saudi Arabia "),
                List.of("retail", "hospitality", ""), null, null, null, null, context());

        assertThat(matches.adjacentIndustries()).containsExactly("consumer goods", "leisure");
        verify(market).matching(argThat(scope ->
                scope.countries().equals(List.of("United Arab Emirates", "Saudi Arabia"))
                        && scope.industries().equals(List.of("retail", "hospitality"))));
        assertThat(recorder.steps()).singleElement().extracting(AssistantStep::label)
                .isEqualTo("Searching retail or hospitality companies in United Arab Emirates or Saudi Arabia");
    }

    @Test
    @DisplayName("common spellings are searched as the universe spells them, and an unknown one is reported back")
    void searchesInTheUniversesSpelling() {
        when(market.matching(any())).thenReturn(new CompanyMatches(9, 9, List.of(), List.of(), List.of()));

        CompanyMatches matches = tools().searchCompanyUniverse(List.of("UAE", "KSA", "Atlantis"),
                List.of("Retail", "Underwater Basket Weaving"), null, null, null, null, context());

        verify(market).matching(argThat(scope ->
                scope.countries().equals(List.of("United Arab Emirates", "Saudi Arabia", "Atlantis"))
                        && scope.industries().equals(List.of("retail", "Underwater Basket Weaving"))));
        assertThat(matches.unrecognisedSpellings()).containsExactly("Atlantis", "Underwater Basket Weaving");
        assertThat(recorder.steps()).singleElement().extracting(AssistantStep::label)
                .isEqualTo("Searching retail or Underwater Basket Weaving companies in United Arab Emirates or "
                        + "Saudi Arabia or Atlantis");
    }

    @Test
    @DisplayName("a company the mandate already filed carries its stage, and the step says how many")
    void marksWhatTheMandateAlreadyHolds() {
        when(market.matching(any())).thenReturn(new CompanyMatches(2, 2, List.of(
                summary("a1", "Lulu Retail"), summary("a2", "Carrefour")), List.of(), List.of()));
        when(triaged.stagesOf(any(), any(), any(), any())).thenReturn(new MandateStages(
                Map.of("a1", TriageCompanyStatus.SHORTLISTED), Map.of("carrefour", TriageCompanyStatus.DECLINED)));

        CompanyMatches matches = tools().searchCompanyUniverse(List.of("United Arab Emirates"), null, null,
                null, null, null, context());

        assertThat(matches.companies()).extracting(MarketCompanySummary::mandateStage)
                .containsExactly("shortlisted", "declined");
        assertThat(recorder.steps()).singleElement().extracting(AssistantStep::detail)
                .isEqualTo("2 matched · 2 already in the mandate");
    }

    @Test
    @DisplayName("an empty search says so rather than reporting zero of zero")
    void saysWhenNothingMatched() {
        assertThat(CompanySearchTools.describeMatches(new CompanyMatches(0, 0, List.of(), List.of(), List.of())))
                .isEqualTo("No companies matched");
        assertThat(CompanySearchTools.describeMatches(new CompanyMatches(12, 12, List.of(), List.of(), List.of())))
                .isEqualTo("12 matched");
    }

    @Test
    @DisplayName("the label reads only the constraints the search was given")
    void describesOnlyWhatWasAsked() {
        assertThat(CompanySearchTools.describeSearch(List.of("Qatar"), List.of(), null, null, null, null))
                .isEqualTo("Searching companies in Qatar");
        assertThat(CompanySearchTools.describeSearch(List.of("Qatar"), List.of("construction"), null, null,
                500L, 5_000L))
                .isEqualTo("Searching construction companies in Qatar with 500–5,000 staff");
        assertThat(CompanySearchTools.describeSearch(List.of(), List.of(), "solar", "ACWA", 1_000L, null))
                .isEqualTo("Searching \"solar\" companies named ACWA with at least 1,000 staff");
    }

    @BeforeEach
    void nothingFiledYet() {
        when(triaged.stagesOf(any(), any(), any(), any())).thenReturn(MandateStages.NONE);
    }

    private CompanySearchTools tools() {
        return new CompanySearchTools(market, adjacency, triaged);
    }

    private static MarketCompanySummary summary(String id, String name) {
        return new MarketCompanySummary(id, name, "retail", "United Arab Emirates", "Dubai", 1_000, null, null,
                null);
    }

    private ToolContext context() {
        return new ToolContext(new AssistantToolContext(UUID.randomUUID(), UUID.randomUUID(), recorder).asMap());
    }
}
