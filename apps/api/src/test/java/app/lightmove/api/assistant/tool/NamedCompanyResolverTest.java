package app.lightmove.api.assistant.tool;

import static app.lightmove.api.assistant.tool.NamedCompanyFinding.Status.NOT_CHECKED;
import static app.lightmove.api.assistant.tool.NamedCompanyFinding.Status.OFF_LIMITS;
import static app.lightmove.api.assistant.tool.NamedCompanyFinding.Status.RESEARCHED;
import static app.lightmove.api.assistant.tool.NamedCompanyFinding.Status.UNIVERSE;
import static app.lightmove.api.assistant.tool.NamedCompanyFinding.Status.UNVERIFIED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.enrichment.company.service.CompanyResearch;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A remembered name reaches the answer only as what the universe or LinkedIn says it is. */
class NamedCompanyResolverTest {

    private static final String UAE = "United Arab Emirates";

    private final ApolloCompanyQueryService market = mock(ApolloCompanyQueryService.class);
    private final CompanyResearch research = mock(CompanyResearch.class);
    private Duration deadline = Duration.ofSeconds(5);

    @BeforeEach
    void nothingKnown() {
        when(market.largestNamed(anyString(), any(), anyInt())).thenReturn(Optional.empty());
        when(market.matchEmployer(any(), anyString())).thenReturn(Optional.empty());
        when(research.byName(anyString(), anyString(), any())).thenReturn(Optional.empty());
        when(research.byNameAnywhere(anyString(), any())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("a name the universe holds in that country costs nothing, even where it holds others abroad")
    void findsTheUniverseRowInTheCountry() {
        when(market.largestNamed("Carrefour", UAE, 0)).thenReturn(Optional.of(row("a1", "Carrefour", UAE, 9_000)));

        NamedCompanyFinding found = only(resolve(names("Carrefour")));

        assertThat(found.status()).isEqualTo(UNIVERSE);
        assertThat(found.apolloAccountId()).isEqualTo("a1");
    }

    @Test
    @DisplayName("a company found on LinkedIn carries the provider's figures")
    void keepsWhatWasResearched() {
        when(research.byName(eq("DAMAC Properties"), eq(UAE), any()))
                .thenReturn(Optional.of(page("damac-properties", "DAMAC Properties", 7_038)));

        NamedCompanyResolver.ResolvedName resolved = resolve(names("DAMAC Properties")).names().getFirst();

        assertThat(resolved.finding().status()).isEqualTo(RESEARCHED);
        assertThat(resolved.finding().linkedinSlug()).isEqualTo("damac-properties");
        assertThat(resolved.details().numEmployees()).isEqualTo(7_038);
    }

    @Test
    @DisplayName("a researched page the universe already holds under a longer name is the universe row")
    void prefersTheUniverseRowForAResearchedPage() {
        when(research.byName(eq("Aldar Properties"), eq(UAE), any()))
                .thenReturn(Optional.of(page("aldar_properties", "ALDAR", 9_714)));
        when(market.matchEmployer("aldar_properties", "ALDAR"))
                .thenReturn(Optional.of(row("a7", "Aldar Properties PJSC", UAE, 9_000)));

        assertThat(only(resolve(names("Aldar Properties"))).apolloAccountId()).isEqualTo("a7");
    }

    @Test
    @DisplayName("a global brand run by a local partner is the partner, marked with the brand it runs")
    void prefersTheLocalOperator() {
        when(market.largestNamed("Majid Al Futtaim", UAE, 0))
                .thenReturn(Optional.of(row("a3", "Majid Al Futtaim", UAE, 27_600)));

        NamedCompanyFinding found = only(resolve(List.of(new NamedCompanyRequest("Carrefour", "Majid Al Futtaim"))));

        assertThat(found.askedName()).isEqualTo("Carrefour");
        assertThat(found.apolloAccountId()).isEqualTo("a3");
        assertThat(found.operates()).isEqualTo("Carrefour");
    }

    @Test
    @DisplayName("a global company not found in the country is found as its own page abroad")
    void findsAGlobalCompanyAbroad() {
        when(research.byNameAnywhere(eq("IKEA"), any())).thenReturn(Optional.of(page("ikea", "IKEA", 160_000)));

        NamedCompanyFinding found = only(resolve(names("IKEA")));

        assertThat(found.status()).isEqualTo(RESEARCHED);
        assertThat(found.global()).isTrue();
    }

    @Test
    @DisplayName("the global universe lookup asks only for a company big enough to be global")
    void asksTheUniverseForABigCompanyAbroad() {
        when(market.largestNamed(eq("Nakheel"), isNull(), eq(CompanyResearch.MIN_EMPLOYEES_ANYWHERE)))
                .thenReturn(Optional.empty());

        assertThat(only(resolve(names("Nakheel"))).status()).isEqualTo(UNVERIFIED);
    }

    @Test
    @DisplayName("an off-limits company is reported, never offered")
    void reportsAnOffLimitsCompany() {
        when(market.largestNamed("Barred Co", UAE, 0)).thenReturn(Optional.of(row("a2", "Barred Co", UAE, 500)));

        NamedCompanyResolver.Resolution resolution = new NamedCompanyResolver(market, research, properties())
                .resolve(names("Barred Co"), UAE, Set.of("a2"));

        assertThat(resolution.names().getFirst().finding().status()).isEqualTo(OFF_LIMITS);
    }

    @Test
    @DisplayName("a lookup past the deadline is reported as not checked rather than holding the answer")
    void givesUpAtTheDeadline() {
        deadline = Duration.ofMillis(100);
        when(research.byName(eq("Slow Co"), anyString(), any())).thenAnswer(call -> {
            Thread.sleep(2_000);
            return Optional.empty();
        });

        assertThat(only(resolve(names("Slow Co"))).status()).isEqualTo(NOT_CHECKED);
    }

    private NamedCompanyResolver.Resolution resolve(List<NamedCompanyRequest> companies) {
        return new NamedCompanyResolver(market, research, properties()).resolve(companies, UAE, Set.of());
    }

    private static NamedCompanyFinding only(NamedCompanyResolver.Resolution resolution) {
        assertThat(resolution.names()).hasSize(1);
        return resolution.names().getFirst().finding();
    }

    private LightMoveProperties properties() {
        LightMoveProperties properties = mock(LightMoveProperties.class, RETURNS_DEEP_STUBS);
        when(properties.assistant().nameLookupParallelism()).thenReturn(3);
        when(properties.assistant().nameLookupDeadline()).thenReturn(deadline);
        when(properties.assistant().maxVendorSearchesPerAsk()).thenReturn(15);
        return properties;
    }

    static List<NamedCompanyRequest> names(String... names) {
        return Arrays.stream(names).map(name -> new NamedCompanyRequest(name, null)).toList();
    }

    static CompanyRow row(String id, String name, String country, int employees) {
        return new CompanyRow(id, name, "real estate", country, "Dubai", employees, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(), List.of());
    }

    static CapturedCompanyDetails page(String slug, String name, int employees) {
        return new CapturedCompanyDetails(name, "Real Estate", UAE, "Dubai", employees, null,
                "https://" + slug + ".example", "https://www.linkedin.com/company/" + slug, 2002,
                "A developer.", null, null, null);
    }
}
