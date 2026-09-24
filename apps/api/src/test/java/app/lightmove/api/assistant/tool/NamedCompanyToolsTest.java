package app.lightmove.api.assistant.tool;

import static app.lightmove.api.assistant.tool.NamedCompanyFinding.Status.NOT_CHECKED;
import static app.lightmove.api.assistant.tool.NamedCompanyFinding.Status.OFF_LIMITS;
import static app.lightmove.api.assistant.tool.NamedCompanyFinding.Status.RESEARCHED;
import static app.lightmove.api.assistant.tool.NamedCompanyFinding.Status.UNIVERSE;
import static app.lightmove.api.assistant.tool.NamedCompanyFinding.Status.UNVERIFIED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.lightmove.api.enrichment.company.service.CompanyResearch;
import app.lightmove.api.strategy.model.CompanyExclusion;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.strategy.model.CompanyScope;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import app.lightmove.api.strategy.service.StrategyService;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

/** A remembered name reaches the answer only as what the universe or LinkedIn says it is. */
class NamedCompanyToolsTest {

    private static final String UAE = "United Arab Emirates";

    private final ApolloCompanyQueryService market = mock(ApolloCompanyQueryService.class);
    private final StrategyService strategies = mock(StrategyService.class);
    private final CompanyResearch research = mock(CompanyResearch.class);
    private final TurnRecorder recorder = new TurnRecorder(step -> { });

    @BeforeEach
    void nothingKnown() {
        when(market.matchEmployer(any(), anyString())).thenReturn(Optional.empty());
        when(research.byName(anyString(), anyString())).thenReturn(Optional.empty());
        when(research.byNameAnywhere(anyString())).thenReturn(Optional.empty());
        when(strategies.scopeOf(any(), any())).thenReturn(CompanyScope.unfiltered());
    }

    @Test
    @DisplayName("a name the universe holds in that country costs nothing and carries its account id")
    void findsTheUniverseFirst() {
        when(market.matchEmployer(isNull(), eq("Emaar"))).thenReturn(Optional.of(row("a1", "Emaar", UAE)));

        NamedCompanies answer = tools().lookUpCompaniesByName(names("Emaar"), UAE, context());

        assertThat(answer.companies()).singleElement().satisfies(found -> {
            assertThat(found.status()).isEqualTo(UNIVERSE);
            assertThat(found.apolloAccountId()).isEqualTo("a1");
        });
        assertThat(recorder.researched()).isEmpty();
    }

    @Test
    @DisplayName("a small namesake in another country is not taken for the company asked about")
    void ignoresAUniverseNamesakeAbroad() {
        when(market.matchEmployer(isNull(), eq("Nakheel"))).thenReturn(Optional.of(row("a9", "Nakheel", "India", 40)));

        NamedCompanies answer = tools().lookUpCompaniesByName(names("Nakheel"), UAE, context());

        assertThat(answer.companies()).extracting(NamedCompanyFinding::status).containsExactly(UNVERIFIED);
    }

    @Test
    @DisplayName("a company found on LinkedIn is kept for the card with the provider's figures")
    void keepsWhatWasResearched() {
        when(research.byName("DAMAC Properties", UAE)).thenReturn(Optional.of(page("damac-properties", "DAMAC Properties", 7_038)));

        NamedCompanies answer = tools().lookUpCompaniesByName(names("DAMAC Properties"), UAE, context());

        assertThat(answer.companies()).singleElement().satisfies(found -> {
            assertThat(found.status()).isEqualTo(RESEARCHED);
            assertThat(found.linkedinSlug()).isEqualTo("damac-properties");
            assertThat(found.employees()).isEqualTo(7_038);
        });
        assertThat(recorder.researched()).containsOnlyKeys("damac-properties");
    }

    @Test
    @DisplayName("a researched company the universe already holds by its LinkedIn page is the universe row")
    void prefersTheUniverseRowForAResearchedPage() {
        when(research.byName("Aldar Properties", UAE)).thenReturn(Optional.of(page("aldar_properties", "ALDAR", 9_714)));
        when(market.matchEmployer(eq("aldar_properties"), eq("ALDAR"))).thenReturn(Optional.of(row("a7", "Aldar Properties PJSC", UAE)));

        NamedCompanies answer = tools().lookUpCompaniesByName(names("Aldar Properties"), UAE, context());

        assertThat(answer.companies()).extracting(NamedCompanyFinding::apolloAccountId).containsExactly("a7");
        assertThat(recorder.researched()).isEmpty();
    }

    @Test
    @DisplayName("an off-limits company and an unknown name are reported, never offered")
    void reportsWhatCannotBeOffered() {
        when(market.matchEmployer(isNull(), eq("Barred Co"))).thenReturn(Optional.of(row("a2", "Barred Co", UAE)));
        when(strategies.scopeOf(any(), any())).thenReturn(new CompanyScope(List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), null, null, List.of("a2"), CompanyExclusion.NONE, null));

        NamedCompanies answer = tools().lookUpCompaniesByName(names("Barred Co", "Gulf Horizon Realty"), UAE, context());

        assertThat(answer.companies()).extracting(NamedCompanyFinding::status).containsExactly(OFF_LIMITS, UNVERIFIED);
        assertThat(recorder.steps()).singleElement().satisfies(step -> {
            assertThat(step.label()).isEqualTo("Checking 2 companies from knowledge");
            assertThat(step.detail()).isEqualTo("1 off limits · 1 couldn't be verified");
        });
    }

    @Test
    @DisplayName("a global brand run by a local partner is the partner, marked with the brand it runs")
    void prefersTheLocalOperator() {
        when(market.matchEmployer(isNull(), eq("Majid Al Futtaim")))
                .thenReturn(Optional.of(row("a3", "Majid Al Futtaim", UAE)));

        NamedCompanies answer = tools().lookUpCompaniesByName(
                List.of(new NamedCompanyRequest("Carrefour", "Majid Al Futtaim")), UAE, context());

        assertThat(answer.companies()).singleElement().satisfies(found -> {
            assertThat(found.askedName()).isEqualTo("Carrefour");
            assertThat(found.apolloAccountId()).isEqualTo("a3");
            assertThat(found.operates()).isEqualTo("Carrefour");
        });
        assertThat(recorder.operatedBrands()).containsEntry("a3", "Carrefour");
    }

    @Test
    @DisplayName("a global company not found in the country is found as its own page abroad")
    void findsAGlobalCompanyAbroad() {
        when(research.byNameAnywhere("IKEA")).thenReturn(Optional.of(page("ikea", "IKEA", 160_000)));

        NamedCompanies answer = tools().lookUpCompaniesByName(names("IKEA"), UAE, context());

        assertThat(answer.companies()).singleElement().satisfies(found -> {
            assertThat(found.status()).isEqualTo(RESEARCHED);
            assertThat(found.global()).isTrue();
        });
        assertThat(recorder.steps().getFirst().detail()).isEqualTo("1 researched · 1 global");
    }

    @Test
    @DisplayName("a lookup past the deadline is reported as not checked rather than holding the answer")
    void givesUpAtTheDeadline() {
        when(research.byName(eq("Slow Co"), anyString())).thenAnswer(call -> {
            Thread.sleep(2_000);
            return Optional.empty();
        });

        NamedCompanies answer = new NamedCompanyTools(market, strategies, research, Duration.ofMillis(100))
                .lookUpCompaniesByName(names("Slow Co"), UAE, context());

        assertThat(answer.companies()).extracting(NamedCompanyFinding::status).containsExactly(NOT_CHECKED);
    }

    @Test
    @DisplayName("names are looked up once per answer, because every lookup is billed")
    void looksUpOncePerAnswer() {
        tools().lookUpCompaniesByName(names("Emaar"), UAE, context());

        NamedCompanies again = tools().lookUpCompaniesByName(names("Nakheel"), UAE, context());

        assertThat(again.companies()).isEmpty();
        assertThat(again.note()).isNotBlank();
    }

    private static List<NamedCompanyRequest> names(String... names) {
        return Arrays.stream(names).map(name -> new NamedCompanyRequest(name, null)).toList();
    }

    private NamedCompanyTools tools() {
        return new NamedCompanyTools(market, strategies, research);
    }

    private ToolContext context() {
        return new ToolContext(new AssistantToolContext(UUID.randomUUID(), UUID.randomUUID(), recorder).asMap());
    }

    private static CompanyRow row(String id, String name, String country) {
        return row(id, name, country, 5_000);
    }

    private static CompanyRow row(String id, String name, String country, int employees) {
        return new CompanyRow(id, name, "real estate", country, "Dubai", employees, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(), List.of());
    }

    private static CapturedCompanyDetails page(String slug, String name, int employees) {
        return new CapturedCompanyDetails(name, "Real Estate", UAE, "Dubai", employees, null,
                "https://" + slug + ".example", "https://www.linkedin.com/company/" + slug, 2002,
                "A developer.", null, null, null);
    }
}
