package app.lightmove.api.assistant.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.lightmove.api.assistant.model.AssistantProposal;
import app.lightmove.api.assistant.model.ProposalOrigin;
import app.lightmove.api.assistant.service.AssistantEventSink;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.strategy.model.CompanyExclusion;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.strategy.model.CompanyScope;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import app.lightmove.api.strategy.service.StrategyService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import tools.jackson.databind.ObjectMapper;

/**
 * That a proposal says only what the market said, and only about companies the mandate may see.
 *
 * <p>The card this feeds is where a consultant decides to file forty companies into a client's
 * mandate, so every field on it has to be one we resolved rather than one the model produced.
 */
class ProposalToolsTest {

    private static final int CAP = 25;
    private static final UUID PROJECT = UUID.randomUUID();

    private final ApolloCompanyQueryService market = mock(ApolloCompanyQueryService.class);
    private final StrategyService strategies = mock(StrategyService.class);
    private final ObjectMapper json = new ObjectMapper();
    private final List<Map<String, Object>> emitted = new ArrayList<>();

    @Test
    @DisplayName("every field comes from the market row, not from what the model said")
    void buildsRowsFromTheResolvedMarket() {
        marketHolding(row("a1", "Saudi Electricity Company", "Saudi Arabia", 32_000));

        tools().proposeCompanies(PROJECT.toString(), "Six IPPs", List.of("a1"), context());

        assertThat(proposal().companies()).singleElement().satisfies(company -> {
            assertThat(company.companyName()).isEqualTo("Saudi Electricity Company");
            assertThat(company.country()).isEqualTo("Saudi Arabia");
            assertThat(company.employees()).isEqualTo(32_000);
            assertThat(company.apolloAccountId()).isEqualTo("a1");
            assertThat(company.origin()).isEqualTo(ProposalOrigin.UNIVERSE);
        });
    }

    @Test
    @DisplayName("a company the mandate ruled off limits never reaches the card")
    void dropsOffLimitsBeforeProposing() {
        marketHolding(row("a1", "ACWA Power", "Saudi Arabia", 4_000),
                row("a2", "Barred Co", "Saudi Arabia", 900));
        offLimits("a2");

        ProposalPlaced placed = tools()
                .proposeCompanies(PROJECT.toString(), "Two", List.of("a1", "a2"), context());

        // Proposing one a client has ruled out and silently dropping it on the way in is the
        // dishonest count this whole issue exists to avoid.
        assertThat(proposal().companies()).extracting("apolloAccountId").containsExactly("a1");
        assertThat(placed.proposed()).isEqualTo(1);
        assertThat(placed.dropped()).isEqualTo(1);
    }

    @Test
    @DisplayName("an id the universe no longer carries is dropped and counted")
    void countsWhatTheUniverseCouldNotResolve() {
        marketHolding(row("a1", "ACWA Power", "Saudi Arabia", 4_000));

        ProposalPlaced placed = tools()
                .proposeCompanies(PROJECT.toString(), "Two", List.of("a1", "gone"), context());

        assertThat(placed.proposed()).isEqualTo(1);
        assertThat(placed.dropped()).isEqualTo(1);
    }

    @Test
    @DisplayName("more companies than the row limit are capped rather than proposed whole")
    void capsAtTheRowLimit() {
        List<CompanyRow> many = IntStream.range(0, 40)
                .mapToObj(index -> row("a" + index, "Company " + index, "Qatar", 100))
                .toList();
        marketHolding(many.toArray(CompanyRow[]::new));

        ProposalPlaced placed = tools().proposeCompanies(PROJECT.toString(), "Many",
                many.stream().map(CompanyRow::apolloAccountId).toList(), context());

        assertThat(placed.proposed()).isEqualTo(CAP);
    }

    @Test
    @DisplayName("the model's title is flattened to one line and capped")
    void flattensTheTitleItWasGiven() {
        marketHolding(row("a1", "ACWA Power", "Saudi Arabia", 4_000));

        tools().proposeCompanies(PROJECT.toString(), "Six\nIPPs   in\tSaudi", List.of("a1"), context());

        // It is rendered as a card heading, so a newline in it is a line the panel never laid out.
        assertThat(proposal().title()).isEqualTo("Six IPPs in Saudi");
    }

    @Test
    @DisplayName("a proposal names no stage — the person filing chooses that")
    void proposesWhatAndNeverWhere() {
        marketHolding(row("a1", "ACWA Power", "Saudi Arabia", 4_000));

        tools().proposeCompanies(PROJECT.toString(), "One", List.of("a1"), context());

        assertThat(json.convertValue(emitted.getFirst(), Map.class)).doesNotContainKey("status");
    }

    private ProposalTools tools() {
        LightMoveProperties properties = mock(LightMoveProperties.class, RETURNS_DEEP_STUBS);
        when(properties.assistant().toolRowLimit()).thenReturn(CAP);
        return new ProposalTools(market, strategies, json, properties);
    }

    private AssistantProposal proposal() {
        assertThat(emitted).as("the tool's only effect is the event it emits").hasSize(1);
        return json.convertValue(emitted.getFirst(), AssistantProposal.class);
    }

    private void marketHolding(CompanyRow... rows) {
        List<CompanyRow> held = List.of(rows);
        when(market.byAccountIds(any())).thenAnswer(call -> {
            List<String> asked = call.getArgument(0);
            return held.stream().filter(row -> asked.contains(row.apolloAccountId())).toList();
        });
        when(strategies.scopeOf(any(), any())).thenReturn(CompanyScope.unfiltered());
    }

    private void offLimits(String... accountIds) {
        when(strategies.scopeOf(any(), any())).thenReturn(new CompanyScope(List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), null, null, List.of(accountIds),
                CompanyExclusion.NONE, null));
    }

    private ToolContext context() {
        AssistantEventSink sink = new AssistantEventSink() {
            @Override
            public void delta(String text) {
            }

            @Override
            public void proposal(Map<String, Object> payload) {
                emitted.add(payload);
            }
        };
        return new ToolContext(ToolCallerContext.of(
                new AssistantToolCaller(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                sink));
    }

    private static CompanyRow row(String id, String name, String country, Integer employees) {
        return new CompanyRow(id, name, "oil & energy", country, "Riyadh", employees, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, List.of(), List.of(), List.of(), List.of());
    }
}
