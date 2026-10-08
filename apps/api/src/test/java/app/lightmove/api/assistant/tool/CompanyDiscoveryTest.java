package app.lightmove.api.assistant.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.lightmove.api.enrichment.company.model.CompanyActivityQuery;
import app.lightmove.api.enrichment.company.model.VendorCompanyRecord;
import app.lightmove.api.enrichment.company.model.VendorSearchAllowance;
import app.lightmove.api.enrichment.company.service.CompanyResearch;
import app.lightmove.api.strategy.dto.FacetCount;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.strategy.model.ScoredCompanyRow;
import app.lightmove.api.strategy.model.SimilarityScope;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import app.lightmove.api.strategy.service.SectorTaxonomy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** "Like" starts strict and gives up headcount, then sector, until enough are found — never the country. */
class CompanyDiscoveryTest {

    private static final String UAE = "United Arab Emirates";
    private static final List<String> RETAIL_SECTOR = List.of("retail", "luxury goods & jewelry");
    private static final List<String> NICHE = List.of("luxury watches", "watch retail", "jewellery");

    private final ApolloCompanyQueryService market = mock(ApolloCompanyQueryService.class);
    private final CompanyResearch research = mock(CompanyResearch.class);
    private final SectorTaxonomy taxonomy = mock(SectorTaxonomy.class);
    private final CompanyDiscovery discovery = new CompanyDiscovery(market, research, taxonomy,
            new LinkedInTopUp(market, research));
    private final List<SimilarityScope> asked = new ArrayList<>();

    @BeforeEach
    void aWatchRetailer() {
        when(taxonomy.sectorOf("luxury goods & jewelry")).thenReturn(RETAIL_SECTOR);
        when(market.distinctiveKeywords(anyList(), anyInt())).thenReturn(NICHE);
        when(market.matchEmployer(any(), anyString())).thenReturn(Optional.empty());
        when(market.similarTo(any())).thenAnswer(call -> {
            asked.add(call.getArgument(0));
            return List.of();
        });
    }

    @Test
    @DisplayName("a name the database holds twice is two candidates, and a shell company a tenth their size is none")
    void offersEveryCompanyANameCouldMean() {
        when(market.namedLike(List.of("seddiqi"), UAE, CompanyDiscovery.MAX_CANDIDATES)).thenReturn(List.of(
                row("holding", "Seddiqi Holding", "retail", 1400),
                row("sons", "Ahmed Seddiqi & Sons", "luxury goods & jewelry", 950),
                row("shell", "Seddiqi Trading", "retail", 5)));

        CompanyDiscovery.Identified identified = discovery.identify("Seddiqi Holdings", "UAE", 15);

        assertThat(identified.candidates()).extracting(CompanyDiscovery.Found::name)
                .containsExactly("Seddiqi Holding", "Ahmed Seddiqi & Sons");
        assertThat(identified.vendorSearches()).isZero();
        verify(research, never()).pagesNamed(anyString(), any(), any());
    }

    @Test
    @DisplayName("a name the database lacks is searched on LinkedIn, and a page it holds after all is its row")
    void looksUpANameTheDatabaseLacksOnLinkedIn() {
        when(market.namedLike(anyList(), any(), anyInt())).thenReturn(List.of());
        when(research.pagesNamed(eq("Kalem"), eq("UAE"), any())).thenAnswer(call -> {
            call.getArgument(2, VendorSearchAllowance.class).take();
            return List.of(page("kalem-group", "Kalem Group", 800), page("kalem-labs", "Kalem Labs", 300));
        });
        when(market.matchEmployer("kalem-group", "Kalem Group"))
                .thenReturn(Optional.of(row("k1", "Kalem Group", "retail", 800)));

        CompanyDiscovery.Identified identified = discovery.identify("Kalem", "UAE", 15);

        assertThat(identified.candidates()).extracting(CompanyDiscovery.Found::accountId)
                .containsExactly("k1", null);
        assertThat(identified.vendorSearches()).isEqualTo(1);
    }

    @Test
    @DisplayName("enough companies in the niche, sector and size ask nothing more and loosen nothing")
    void stopsAtTheFirstStepThatFindsEnough() {
        doAnswer(call -> {
            asked.add(call.getArgument(0));
            return List.of(scored("rivoli", "Rivoli", 2000), scored("damas", "Damas", 1900));
        }).when(market).similarTo(any());

        CompanyDiscovery.Discovered found = discovery.similarTo(seddiqi(), List.of(UAE), 2, Set.of(), 15);

        assertThat(found.companies()).extracting(CompanyDiscovery.Found::name).containsExactly("Rivoli", "Damas");
        assertThat(found.loosened()).isEmpty();
        assertThat(asked).singleElement().satisfies(scope -> {
            assertThat(scope.industries()).isEqualTo(RETAIL_SECTOR);
            assertThat(scope.minEmployees()).isEqualTo(237L);
            assertThat(scope.maxEmployees()).isEqualTo(3800L);
            assertThat(scope.excludedAccountIds()).contains("seddiqi");
            assertThat(scope.countries()).containsExactly(UAE);
        });
    }

    @Test
    @DisplayName("too few loosens headcount, then sector, then niche — saying so each time, never the country")
    void loosensOneCriterionAtATime() {
        when(research.isEnabled()).thenReturn(false);

        CompanyDiscovery.Discovered found = discovery.similarTo(seddiqi(), List.of(UAE), 10, Set.of("offlimits"), 15);

        assertThat(found.loosened()).containsExactly(
                "widened the size to between a tenth and ten times its headcount",
                "dropped the size limit",
                "looked beyond its sector",
                "took companies sharing a single niche keyword");
        assertThat(asked).extracting(SimilarityScope::minEmployees).containsExactly(237L, 95L, null, null, null);
        assertThat(asked).extracting(SimilarityScope::industries)
                .containsExactly(RETAIL_SECTOR, RETAIL_SECTOR, RETAIL_SECTOR, List.of(), RETAIL_SECTOR);
        assertThat(asked).extracting(SimilarityScope::minShared).containsExactly(2, 2, 2, 2, 1);
        assertThat(asked).allSatisfy(scope -> {
            assertThat(scope.countries()).containsExactly(UAE);
            assertThat(scope.excludedAccountIds()).contains("offlimits", "seddiqi");
        });
        verify(research, never()).byActivity(any(), any());
    }

    @Test
    @DisplayName("what the database comes up short on is bought from LinkedIn by the niche's own words, nothing more")
    void topsUpFromLinkedInByTheNichesWords() {
        when(research.isEnabled()).thenReturn(true);
        doAnswer(call -> {
            SimilarityScope scope = call.getArgument(0);
            return scope.industries().isEmpty() ? List.of() : List.of(scored("rivoli", "Rivoli", 2000));
        }).when(market).similarTo(any());
        when(research.byActivity(any(), any())).thenAnswer(call -> {
            call.getArgument(1, VendorSearchAllowance.class).take();
            CompanyActivityQuery query = call.getArgument(0);
            return query.industries().isEmpty() ? List.of(page("time-house", "Time House", 240))
                    : List.of(page("bin-hendi", "BinHendi", 750), page("seddiqi-sons", "Seddiqi", 950));
        });

        CompanyDiscovery.Discovered found = discovery.similarTo(seddiqi(), List.of(UAE), 4, Set.of(), 15);

        assertThat(found.companies()).extracting(CompanyDiscovery.Found::name)
                .containsExactly("Rivoli", "BinHendi", "Time House");
        assertThat(found.searchedLinkedIn()).isTrue();
        assertThat(found.vendorSearches()).isEqualTo(2);
        ArgumentCaptor<CompanyActivityQuery> query = ArgumentCaptor.forClass(CompanyActivityQuery.class);
        verify(research, times(2)).byActivity(query.capture(), any());
        CompanyActivityQuery inSector = query.getAllValues().getFirst();
        assertThat(inSector.words()).containsExactly("watch", "jewellery");
        assertThat(inSector.countryCodes()).containsExactly("AE");
        assertThat(inSector.industries()).contains("Retail", "Retail Luxury Goods and Jewelry");
        assertThat(inSector.minEmployees()).isEqualTo(95);
        assertThat(inSector.maxEmployees()).isEqualTo(9500);
        assertThat(inSector.excludedSlugs()).contains("seddiqi-sons");
        assertThat(inSector.size()).isEqualTo(3);
        CompanyActivityQuery anySector = query.getAllValues().getLast();
        assertThat(anySector.industries()).isEmpty();
        assertThat(anySector.countryCodes()).containsExactly("AE");
        assertThat(anySector.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("an activity is searched as the database's own keywords for each word, singular or plural")
    void searchesAnActivityByTheKeywordsItIsDescribedWith() {
        when(research.isEnabled()).thenReturn(false);
        when(market.keywordSuggestions("watch", 40, 2)).thenReturn(List.of(
                new FacetCount("watches", "watches", 112), new FacetCount("watch retail", "watch retail", 30)));
        when(market.keywordSuggestions("distributor", 40, 2)).thenReturn(List.of(
                new FacetCount("watch distributor", "watch distributor", 4)));

        discovery.byActivity(List.of("watches", "distributors"), List.of(UAE), List.of("wholesale"), null, 500L, 10,
                Set.of(), 15);

        assertThat(asked).singleElement().satisfies(scope -> {
            assertThat(scope.anchorKeywords()).containsExactly("watches", "watch retail", "watch distributor");
            assertThat(scope.minShared()).isEqualTo(1);
            assertThat(scope.maxEmployees()).isEqualTo(500L);
            assertThat(scope.industries()).containsExactly("wholesale");
        });
    }

    @Test
    @DisplayName("the words a LinkedIn search keys on are the ones the niche keywords share, generic ones left out")
    void picksTheNichesCommonWords() {
        assertThat(NicheWords.of(List.of("luxury watches", "watch retail", "luxury watch repair",
                "fine jewellery", "customer experience")))
                .startsWith("watch")
                .contains("jewellery", "repair")
                .doesNotContain("luxury", "retail", "customer", "experience");
    }

    private static CompanyDiscovery.Found seddiqi() {
        CompanyRow row = new CompanyRow("seddiqi", "Ahmed Seddiqi & Sons", "luxury goods & jewelry", UAE, "Dubai", 950,
                null, null, null, null, null, "http://www.linkedin.com/company/seddiqi-sons", null, null, null,
                null, null, null, null, null, null, null, null, NICHE, List.of(), List.of(), List.of());
        return CompanyDiscovery.Found.of(row);
    }

    private static ScoredCompanyRow scored(String id, String name, int employees) {
        return new ScoredCompanyRow(row(id, name, "retail", employees), 10, List.of("luxury watches"));
    }

    static CompanyRow row(String id, String name, String industry, int employees) {
        return new CompanyRow(id, name, industry, UAE, "Dubai", employees, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(), List.of());
    }

    private static VendorCompanyRecord page(String slug, String name, int employees) {
        return new VendorCompanyRecord(slug, name, "Retail", UAE, "Dubai", employees, null,
                "https://www.linkedin.com/company/" + slug, null, null, null, List.of("watches"), "{}");
    }
}
