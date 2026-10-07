package app.lightmove.api.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.lightmove.api.assistant.constant.RefinementKind;
import app.lightmove.api.assistant.model.AssistantRefinement;
import app.lightmove.api.assistant.model.AssistantRefinements;
import app.lightmove.api.assistant.tool.AssistantToolContext;
import app.lightmove.api.assistant.tool.MarketAsk;
import app.lightmove.api.assistant.tool.TurnRecorder;
import app.lightmove.api.core.config.AssistantSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.strategy.constant.CompanySortField;
import app.lightmove.api.strategy.constant.SortDirection;
import app.lightmove.api.strategy.model.CompanyExclusion;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.strategy.model.CompanyScope;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import app.lightmove.api.strategy.service.IndustryAdjacency;
import app.lightmove.api.strategy.service.StrategyService;
import app.lightmove.api.strategy.service.TriagedCompanyLookup;
import app.lightmove.api.triagecompany.dto.TriageCountsDto;
import app.lightmove.api.triagecompany.service.TriageCompanyReadService;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.ToLongFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The buttons under an answer steer the mandate's universe toward 50–75 companies, on counts alone. */
class RefinementAdvisorTest {

    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final UUID PROJECT = UUID.randomUUID();
    private static final MarketAsk RETAIL_IN_UAE = new MarketAsk(List.of("United Arab Emirates"), List.of("retail"),
            null, null, null);

    private final ApolloCompanyQueryService market = mock(ApolloCompanyQueryService.class);
    private final StrategyService strategies = mock(StrategyService.class);
    private final TriagedCompanyLookup filedCompanies = mock(TriagedCompanyLookup.class);
    private final TriageCompanyReadService triaged = mock(TriageCompanyReadService.class);
    private final IndustryAdjacency adjacency = mock(IndustryAdjacency.class);
    private final TurnRecorder recorder = new TurnRecorder(step -> { });

    @BeforeEach
    void anUnfilteredMandate() {
        when(strategies.scopeOf(WORKSPACE, PROJECT)).thenReturn(CompanyScope.unfiltered());
        when(filedCompanies.exclusionFor(PROJECT)).thenReturn(CompanyExclusion.NONE);
    }

    @Test
    @DisplayName("an answer that ran no market search offers nothing")
    void staysQuietWithoutASearch() {
        advisor().offer(context());

        assertThat(recorder.refinements()).isNull();
    }

    @Test
    @DisplayName("too few widens, landing inside the target first, and declined companies count for nothing")
    void widensASmallUniverse() {
        mandateHolds(8, 2, 30);
        countNewCompanies(scope -> {
            if (scope.countries().contains("Saudi Arabia")) {
                return 32;
            }
            if (scope.industries().contains("consumer goods")) {
                return 45;
            }
            return scope.industries().contains("hospitality") ? 80 : 20;
        });
        when(adjacency.neighboursOf("retail")).thenReturn(List.of("consumer goods", "hospitality"));
        recorder.searchedMarket(RETAIL_IN_UAE);

        advisor().offer(context());

        AssistantRefinements offered = recorder.refinements();
        assertThat(offered.inMandate()).isEqualTo(10);
        assertThat(offered.newFromSearch()).isEqualTo(20);
        assertThat(offered.projected()).isEqualTo(30);
        assertThat(offered.options()).hasSize(RefinementAdvisor.MAX_OPTIONS);
        assertThat(offered.options().getFirst()).satisfies(option -> {
            assertThat(option.kind()).isEqualTo(RefinementKind.ADD_INDUSTRY);
            assertThat(option.label()).isEqualTo("Add Consumer goods");
            assertThat(option.prompt()).isEqualTo("Find retail and consumer goods companies in United Arab Emirates");
            assertThat(option.projected()).isEqualTo(55);
        });
        assertThat(offered.options()).extracting(AssistantRefinement::projected).allMatch(projected -> projected > 30);
    }

    @Test
    @DisplayName("too many narrows by a readable headcount floor near the target's middle")
    void narrowsALargeUniverse() {
        mandateHolds(0, 0, 0);
        countNewCompanies(scope -> {
            if (scope.employeeRange() == null) {
                return 300;
            }
            return scope.employeeRange().min() == 1_000L ? 70 : 30;
        });
        CompanyRow sixtySecond = mock(CompanyRow.class);
        when(sixtySecond.numEmployees()).thenReturn(1_137);
        when(market.search(any(), eq(CompanySortField.EMPLOYEES), eq(SortDirection.DESC), eq(61), eq(1)))
                .thenReturn(List.of(sixtySecond));
        recorder.searchedMarket(RETAIL_IN_UAE);

        advisor().offer(context());

        assertThat(recorder.refinements().options())
                .extracting(AssistantRefinement::label, AssistantRefinement::projected)
                .containsExactly(tuple("1,000+ staff only", 70L),
                        tuple("2,500+ staff only", 30L));
    }

    @Test
    @DisplayName("several countries can be narrowed to one")
    void narrowsToOneCountry() {
        mandateHolds(20, 0, 0);
        countNewCompanies(scope -> scope.countries().size() == 1
                ? (scope.countries().contains("Qatar") ? 40 : 15) : 120);
        when(market.search(any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        recorder.searchedMarket(new MarketAsk(List.of("Qatar", "Oman"), List.of("retail"), null, null, null));

        advisor().offer(context());

        assertThat(recorder.refinements().options()).first().satisfies(option -> {
            assertThat(option.kind()).isEqualTo(RefinementKind.ONE_COUNTRY);
            assertThat(option.label()).isEqualTo("Qatar only");
            assertThat(option.projected()).isEqualTo(60);
        });
    }

    @Test
    @DisplayName("inside the target, or with the mandate already past it, the tally stands alone")
    void offersNothingInRange() {
        mandateHolds(40, 0, 0);
        countNewCompanies(scope -> 20);
        recorder.searchedMarket(RETAIL_IN_UAE);

        advisor().offer(context());

        assertThat(recorder.refinements().projected()).isEqualTo(60);
        assertThat(recorder.refinements().options()).isEmpty();

        mandateHolds(90, 0, 0);
        advisor().offer(context());

        assertThat(recorder.refinements().options()).isEmpty();
    }

    @Test
    @DisplayName("a search outside the Gulf is never widened into it")
    void offersNeighboursOnlyInTheGulf() {
        mandateHolds(0, 0, 0);
        countNewCompanies(scope -> scope.countries().size() > 1 ? 60 : 10);
        recorder.searchedMarket(new MarketAsk(List.of("United Kingdom"), List.of(), null, null, null));

        advisor().offer(context());

        assertThat(recorder.refinements().options()).isEmpty();
    }

    @Test
    @DisplayName("a floor is read as a round number either side of the exact headcount")
    void roundsTheFloor() {
        assertThat(RefinementAdvisor.readableFloorsAround(1_137)).containsExactly(1_000L, 2_500L);
        assertThat(RefinementAdvisor.readableFloorsAround(500)).containsExactly(500L);
        assertThat(RefinementAdvisor.readableFloorsAround(12)).containsExactly(50L);
        assertThat(RefinementAdvisor.readableFloorsAround(400_000)).containsExactly(100_000L);
    }

    private void mandateHolds(long inUniverse, long shortlisted, long declined) {
        when(triaged.stageCountsOf(WORKSPACE, PROJECT)).thenReturn(new TriageCountsDto(inUniverse, shortlisted, declined));
    }

    private void countNewCompanies(ToLongFunction<CompanyScope> counts) {
        when(market.count(any())).thenAnswer(call -> counts.applyAsLong(call.getArgument(0)));
    }

    private AssistantToolContext context() {
        return new AssistantToolContext(WORKSPACE, PROJECT, recorder);
    }

    private RefinementAdvisor advisor() {
        LightMoveProperties properties = mock(LightMoveProperties.class);
        when(properties.assistant()).thenReturn(new AssistantSettings("gemini-2.5-flash", 0.2, 512, 12, 25, 250,
                4, 10, 5, Duration.ofSeconds(25), 15, 3, 50, 75, List.of("AE", "SA", "QA", "KW", "BH", "OM")));
        return new RefinementAdvisor(market, strategies, filedCompanies, triaged, adjacency, properties);
    }
}
