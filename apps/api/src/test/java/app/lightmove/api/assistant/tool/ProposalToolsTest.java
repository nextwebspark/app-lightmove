package app.lightmove.api.assistant.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.lightmove.api.assistant.model.AssistantProposal;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.strategy.model.CompanyExclusion;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.strategy.model.CompanyScope;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import app.lightmove.api.strategy.service.StrategyService;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

/** The card says only what the universe said, and only about companies the mandate may see. */
class ProposalToolsTest {

    private static final int CAP = 25;

    private final ApolloCompanyQueryService market = mock(ApolloCompanyQueryService.class);
    private final StrategyService strategies = mock(StrategyService.class);
    private final TurnRecorder recorder = new TurnRecorder(step -> { });

    @Test
    @DisplayName("every field comes from the universe row, not from what the model said")
    void buildsRowsFromTheUniverse() {
        marketHolding(row("a1", "Saudi Electricity Company", "Saudi Arabia", 32_000));

        tools().proposeCompanies("Six IPPs", List.of("a1"), context());

        assertThat(recorder.proposal().companies()).singleElement().satisfies(company -> {
            assertThat(company.apolloAccountId()).isEqualTo("a1");
            assertThat(company.companyName()).isEqualTo("Saudi Electricity Company");
            assertThat(company.country()).isEqualTo("Saudi Arabia");
            assertThat(company.employees()).isEqualTo(32_000);
            assertThat(company.logoUrl()).isEqualTo("https://logos.example/a1");
        });
    }

    @Test
    @DisplayName("a company ruled off limits, or gone from the universe, never reaches the card")
    void dropsOffLimitsAndUnknown() {
        marketHolding(row("a1", "ACWA Power", "Saudi Arabia", 4_000),
                row("a2", "Barred Co", "Saudi Arabia", 900));
        offLimits("a2");

        int carried = tools().proposeCompanies("Two", List.of("a1", "a2", "gone"), context());

        assertThat(carried).isEqualTo(1);
        assertThat(recorder.proposal().companies()).extracting("apolloAccountId").containsExactly("a1");
        assertThat(recorder.steps()).singleElement().satisfies(step -> {
            assertThat(step.label()).isEqualTo("Preparing 3 companies");
            assertThat(step.detail())
                    .isEqualTo("1 on the card, 2 left out (off limits or not found)");
        });
    }

    @Test
    @DisplayName("more companies than the row limit are capped")
    void capsAtTheRowLimit() {
        List<CompanyRow> many = IntStream.range(0, 40)
                .mapToObj(index -> row("a" + index, "Company " + index, "Qatar", 100))
                .toList();
        marketHolding(many.toArray(CompanyRow[]::new));

        int carried = tools().proposeCompanies("Many",
                many.stream().map(CompanyRow::apolloAccountId).toList(), context());

        assertThat(carried).isEqualTo(CAP);
    }

    @Test
    @DisplayName("the model's title is flattened to one line")
    void flattensTheTitle() {
        marketHolding(row("a1", "ACWA Power", "Saudi Arabia", 4_000));

        tools().proposeCompanies("Six\nIPPs   in\tSaudi", List.of("a1"), context());

        assertThat(recorder.proposal().title()).isEqualTo("Six IPPs in Saudi");
    }

    @Test
    @DisplayName("an answer that found companies but proposed none still carries a card of them")
    void cardsWhatWasFoundWhenNothingWasProposed() {
        marketHolding(row("a1", "ACWA Power", "Saudi Arabia", 4_000),
                row("a2", "Marafiq", "Saudi Arabia", 2_400));
        recorder.found(List.of("a2", "a1"));

        tools().proposeWhatWasFound(new AssistantToolContext(UUID.randomUUID(), UUID.randomUUID(), recorder));

        assertThat(recorder.proposal().companies()).extracting("apolloAccountId").containsExactly("a1", "a2");
    }

    @Test
    @DisplayName("the card is passed on the moment it is made, before the answer is written")
    void announcesTheCard() {
        marketHolding(row("a1", "ACWA Power", "Saudi Arabia", 4_000));
        List<AssistantProposal> heard = new ArrayList<>();
        TurnRecorder listening = new TurnRecorder(step -> { }, heard::add);

        tools().proposeCompanies("One", List.of("a1"),
                new ToolContext(new AssistantToolContext(UUID.randomUUID(), UUID.randomUUID(), listening).asMap()));

        assertThat(heard).singleElement().satisfies(card ->
                assertThat(card.companies()).extracting("apolloAccountId").containsExactly("a1"));
    }

    @Test
    @DisplayName("a company researched on LinkedIn joins the card with its page's figures and the brand it runs")
    void cardsAResearchedCompany() {
        marketHolding(row("a1", "ACWA Power", "Saudi Arabia", 4_000));
        recorder.researched("ikea", new CapturedCompanyDetails("IKEA", "Retail", "Sweden", "Delft", 160_000,
                null, null, "https://www.linkedin.com/company/ikea", 1943, null, null, null, null));
        recorder.operates("ikea", "IKEA");

        tools().proposeCompanies("Retailers", List.of("a1", "ikea", "never-researched"), context());

        assertThat(recorder.proposal().companies()).extracting("linkedinSlug", "apolloAccountId", "employees")
                .containsExactly(tuple("ikea", null, 160_000), tuple(null, "a1", 4_000));
        assertThat(recorder.proposal().researched()).containsOnlyKeys("ikea");
    }

    @Test
    @DisplayName("a card the model proposed is left as it chose it")
    void keepsTheModelsCard() {
        marketHolding(row("a1", "ACWA Power", "Saudi Arabia", 4_000),
                row("a2", "Marafiq", "Saudi Arabia", 2_400));
        tools().proposeCompanies("One", List.of("a1"), context());
        recorder.found(List.of("a1", "a2"));

        tools().proposeWhatWasFound(new AssistantToolContext(UUID.randomUUID(), UUID.randomUUID(), recorder));

        assertThat(recorder.proposal().companies()).extracting("apolloAccountId").containsExactly("a1");
    }

    private ProposalTools tools() {
        LightMoveProperties properties = mock(LightMoveProperties.class, RETURNS_DEEP_STUBS);
        when(properties.assistant().toolRowLimit()).thenReturn(CAP);
        return new ProposalTools(market, strategies, properties);
    }

    private ToolContext context() {
        return new ToolContext(new AssistantToolContext(UUID.randomUUID(), UUID.randomUUID(), recorder).asMap());
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

    private static CompanyRow row(String id, String name, String country, Integer employees) {
        return new CompanyRow(id, name, "oil & energy", country, "Riyadh", employees, null, null,
                "https://logos.example/" + id, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, List.of(), List.of(), List.of(), List.of());
    }
}
