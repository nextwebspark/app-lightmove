package app.lightmove.api.assistant.tool;

import static app.lightmove.api.assistant.tool.NamedCompanyFinding.Status.RESEARCHED;
import static app.lightmove.api.assistant.tool.NamedCompanyFinding.Status.UNIVERSE;
import static app.lightmove.api.assistant.tool.NamedCompanyFinding.Status.UNVERIFIED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.strategy.model.CompanyScope;
import app.lightmove.api.strategy.service.StrategyService;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

/** What a name lookup tells the person waiting, and what it keeps for the card. */
class NamedCompanyToolsTest {

    private final NamedCompanyResolver resolver = mock(NamedCompanyResolver.class);
    private final StrategyService strategies = mock(StrategyService.class);
    private final TurnRecorder recorder = new TurnRecorder(step -> { });

    @BeforeEach
    void unfiltered() {
        when(strategies.scopeOf(any(), any())).thenReturn(CompanyScope.unfiltered());
    }

    @Test
    @DisplayName("found companies are kept for the card, with the brand a partner runs and the searches spent")
    void keepsWhatWasFound() {
        CapturedCompanyDetails ikea = NamedCompanyResolverTest.page("ikea", "IKEA", 160_000);
        when(resolver.resolve(any(), any(), any())).thenReturn(new NamedCompanyResolver.Resolution(List.of(
                new NamedCompanyResolver.ResolvedName(finding("Carrefour", UNIVERSE, "a3", null, "Carrefour", false), null),
                new NamedCompanyResolver.ResolvedName(finding("IKEA", RESEARCHED, null, "ikea", null, true), ikea),
                new NamedCompanyResolver.ResolvedName(NamedCompanyFinding.unresolved("Walmart", UNVERIFIED), null)), 4));

        tools().lookUpCompaniesByName(NamedCompanyResolverTest.names("Carrefour", "IKEA", "Walmart"),
                "United Arab Emirates", context());

        assertThat(recorder.foundAccountIds()).containsExactly("a3");
        assertThat(recorder.researched()).containsOnlyKeys("ikea");
        assertThat(recorder.operatedBrands()).containsEntry("a3", "Carrefour");
        assertThat(recorder.vendorSearches()).isEqualTo(4);
        assertThat(recorder.steps()).singleElement().satisfies(step -> {
            assertThat(step.label()).isEqualTo("Checking 3 companies from knowledge");
            assertThat(step.detail()).isEqualTo("1 in the universe · 1 researched · 1 global · 1 couldn't be verified");
        });
    }

    @Test
    @DisplayName("names are looked up once per answer, because every lookup is billed")
    void looksUpOncePerAnswer() {
        when(resolver.resolve(any(), any(), any())).thenReturn(new NamedCompanyResolver.Resolution(List.of(), 0));
        tools().lookUpCompaniesByName(NamedCompanyResolverTest.names("Emaar"), "United Arab Emirates", context());
        recorder.startNameLookup();

        NamedCompanies again = new NamedCompanyTools(mock(NamedCompanyResolver.class), strategies, properties())
                .lookUpCompaniesByName(NamedCompanyResolverTest.names("Nakheel"), "United Arab Emirates", context());

        assertThat(again.companies()).isEmpty();
        assertThat(again.note()).isNotBlank();
    }

    @Test
    @DisplayName("no more names are checked than the settings allow")
    void capsTheNames() {
        NamedCompanyResolver capped = mock(NamedCompanyResolver.class);
        when(capped.resolve(any(), any(), any())).thenReturn(new NamedCompanyResolver.Resolution(List.of(), 0));

        new NamedCompanyTools(capped, strategies, properties()).lookUpCompaniesByName(
                NamedCompanyResolverTest.names("A1", "A2", "A3", "A4", "A5", "A6", "A7", "A8", "A9", "A10", "A11"),
                "Qatar", context());

        verify(capped).resolve(argThat(asked -> asked.size() == 10), any(), any());
        verify(resolver, never()).resolve(any(), any(), any());
    }

    private NamedCompanyTools tools() {
        return new NamedCompanyTools(resolver, strategies, properties());
    }

    private static LightMoveProperties properties() {
        LightMoveProperties properties = mock(LightMoveProperties.class, RETURNS_DEEP_STUBS);
        when(properties.assistant().maxNamesPerLookup()).thenReturn(10);
        return properties;
    }

    private ToolContext context() {
        return new ToolContext(new AssistantToolContext(UUID.randomUUID(), UUID.randomUUID(), recorder).asMap());
    }

    private static NamedCompanyFinding finding(String asked, NamedCompanyFinding.Status status, String accountId,
                                               String slug, String operates, boolean global) {
        return new NamedCompanyFinding(asked, status, accountId, slug, asked, "United Arab Emirates", "Retail",
                "Dubai", 1_000, null, null, operates, global);
    }
}
