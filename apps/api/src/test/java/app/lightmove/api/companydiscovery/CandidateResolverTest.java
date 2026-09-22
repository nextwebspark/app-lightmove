package app.lightmove.api.companydiscovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.lightmove.api.companydiscovery.dto.DiscoveredCompanyDto;
import app.lightmove.api.companydiscovery.model.DiscoveredCandidate;
import app.lightmove.api.companydiscovery.model.HeldCompanies;
import app.lightmove.api.companydiscovery.service.CandidateResolver;
import app.lightmove.api.enrichment.company.service.CompanyResearch;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The rule the whole feature exists for: every figure on a row comes from a record, and a company
 * nobody holds a record of is answered with its name and nothing else.
 */
class CandidateResolverTest {

    private ApolloCompanyQueryService companies;
    private CompanyResearch research;
    private CandidateResolver resolver;

    @BeforeEach
    void setUp() {
        companies = mock(ApolloCompanyQueryService.class);
        research = mock(CompanyResearch.class);
        when(companies.matchEmployer(any(), any())).thenReturn(Optional.empty());
        when(companies.matchByDomain(any())).thenReturn(Optional.empty());
        when(research.of(any())).thenReturn(Optional.empty());
        resolver = new CandidateResolver(companies, research);
    }

    @Test
    @DisplayName("a company nobody holds keeps its name and no figures at all")
    void anUnresolvedCompanyCarriesNoFigures() {
        DiscoveredCompanyDto row = one(candidate("Shamal Energy",
                "https://www.linkedin.com/company/shamal-energy/", "https://shamal.example"));

        assertThat(row.source()).isEqualTo("web");
        assertThat(row.unresolved()).isTrue();
        assertThat(row.companyName()).isEqualTo("Shamal Energy");
        assertThat(row.apolloAccountId()).isNull();
        assertThat(row.industry()).isNull();
        assertThat(row.companyCountry()).isNull();
        assertThat(row.numEmployees()).isNull();
        assertThat(row.annualRevenue()).isNull();
        assertThat(row.foundedYear()).isNull();
        // The homepage was a lookup key and it found nothing. Rendering it as this company's website
        // would publish the one unchecked claim the record exists to keep out.
        assertThat(row.website()).isNull();
        // What the model is allowed to contribute does survive.
        assertThat(row.companyLinkedinUrl()).isEqualTo("https://www.linkedin.com/company/shamal-energy/");
        assertThat(row.reason()).isEqualTo("Regional services player");
        assertThat(row.fit()).isEqualTo(58);
    }

    @Test
    @DisplayName("a universe match takes the market's figures, and not the model's name for it")
    void aUniverseMatchTakesTheMarketRow() {
        when(companies.matchEmployer(eq("acwa-power"), isNull()))
                .thenReturn(Optional.of(marketRow("a1", "ACWA Power")));

        DiscoveredCompanyDto row = one(candidate("ACWA",
                "https://www.linkedin.com/company/acwa-power/", "https://acwapower.example"));

        assertThat(row.source()).isEqualTo("universe");
        assertThat(row.unresolved()).isFalse();
        assertThat(row.apolloAccountId()).isEqualTo("a1");
        // The market's own spelling wins over the model's shorter one.
        assertThat(row.companyName()).isEqualTo("ACWA Power");
        assertThat(row.industry()).isEqualTo("utilities");
        assertThat(row.numEmployees()).isEqualTo(3400);
        // Nothing was bought: the universe answered.
        verify(research, never()).of(any());
    }

    @Test
    @DisplayName("the homepage resolves what the LinkedIn page did not")
    void theHomepageIsTheSecondKey() {
        when(companies.matchByDomain("https://masdar.example"))
                .thenReturn(Optional.of(marketRow("a2", "Masdar")));

        DiscoveredCompanyDto row = one(candidate("Masdar", null, "https://masdar.example"));

        assertThat(row.source()).isEqualTo("universe");
        assertThat(row.apolloAccountId()).isEqualTo("a2");
    }

    @Test
    @DisplayName("a unique exact name resolves; a name the universe holds twice does not")
    void theNameIsTheThirdKeyAndOnlyWhenUnique() {
        when(companies.matchEmployer(isNull(), eq("TAQA")))
                .thenReturn(Optional.of(marketRow("a3", "TAQA")));

        assertThat(one(candidate("TAQA", null, null)).apolloAccountId()).isEqualTo("a3");

        // matchEmployer already answers empty for an ambiguous name — two "Alpha Group" rows are one
        // company nobody identified, and a guess here would be a figure attached to the wrong firm.
        DiscoveredCompanyDto ambiguous = one(candidate("Alpha Group", null, null));
        assertThat(ambiguous.unresolved()).isTrue();
        assertThat(ambiguous.industry()).isNull();
    }

    @Test
    @DisplayName("a vendor record fills the row when the universe has nothing")
    void aVendorRecordFillsTheRow() {
        when(research.of("nebras-power")).thenReturn(Optional.of(vendorFacts()));

        DiscoveredCompanyDto row = one(candidate("Nebras Power",
                "https://www.linkedin.com/company/nebras-power/", null));

        assertThat(row.source()).isEqualTo("researched");
        assertThat(row.unresolved()).isFalse();
        assertThat(row.apolloAccountId()).isNull();
        assertThat(row.numEmployees()).isEqualTo(410);
        assertThat(row.companyCountry()).isEqualTo("Qatar");
    }

    @Test
    @DisplayName("a vendor's own website can still find the universe row the slug missed")
    void theVendorsWebsiteIsTriedAgainstTheUniverse() {
        when(research.of("nebras-power")).thenReturn(Optional.of(vendorFacts()));
        when(companies.matchByDomain("https://nebras.example"))
                .thenReturn(Optional.of(marketRow("a4", "Nebras Power")));

        DiscoveredCompanyDto row = one(candidate("Nebras Power",
                "https://www.linkedin.com/company/nebras-power/", null));

        // The universe holds it after all, under a domain the model never produced.
        assertThat(row.source()).isEqualTo("universe");
        assertThat(row.apolloAccountId()).isEqualTo("a4");
    }

    @Test
    @DisplayName("nothing is bought for a candidate with no LinkedIn page")
    void noSlugMeansNoVendorCall() {
        one(candidate("A Company With No Page", null, "https://nopage.example"));

        verify(research, never()).of(any());
    }

    @Test
    @DisplayName("one company named twice is one row")
    void duplicatesCollapse() {
        List<DiscoveredCompanyDto> rows = resolver.resolve(List.of(
                candidate("Masdar", "https://www.linkedin.com/company/masdar/", null),
                candidate("Masdar Clean Energy", "https://linkedin.com/company/masdar", null),
                candidate("Empower", null, null),
                candidate("  empower  ", null, null)), HeldCompanies.none());

        assertThat(rows).hasSize(2);
    }

    @Test
    @DisplayName("a candidate with no name is not a row")
    void anUnnamedCandidateIsDropped() {
        assertThat(resolver.resolve(List.of(candidate("   ", null, null)), HeldCompanies.none()))
                .isEmpty();
    }

    @Test
    @DisplayName("already in the mandate is a separate fact from where the figures came from")
    void alreadyInMandateIsItsOwnFact() {
        when(companies.matchEmployer(eq("acwa-power"), isNull()))
                .thenReturn(Optional.of(marketRow("a1", "ACWA Power")));
        HeldCompanies held = new HeldCompanies(Set.of("a1"), Set.of(), Set.of(), Set.of());

        DiscoveredCompanyDto row = resolver.resolve(
                List.of(candidate("ACWA", "https://www.linkedin.com/company/acwa-power/", null)),
                held).getFirst();

        // Both, not one or the other: a universe row the mandate already holds is still a universe
        // row, and badging it "web" would misdescribe where its numbers came from.
        assertThat(row.source()).isEqualTo("universe");
        assertThat(row.alreadyInMandate()).isTrue();
    }

    @Test
    @DisplayName("a mandate recognises a company it typed in by name, not only by id")
    void aHandTypedCompanyIsStillRecognised() {
        HeldCompanies held = new HeldCompanies(Set.of(), Set.of("yellow door energy"), Set.of(), Set.of());

        DiscoveredCompanyDto row = resolver.resolve(
                List.of(candidate("Yellow Door Energy", null, null)), held).getFirst();

        assertThat(row.unresolved()).isTrue();
        assertThat(row.alreadyInMandate()).isTrue();
    }

    private DiscoveredCompanyDto one(DiscoveredCandidate candidate) {
        return resolver.resolve(List.of(candidate), HeldCompanies.none()).getFirst();
    }

    private static DiscoveredCandidate candidate(String name, String linkedin, String website) {
        return new DiscoveredCandidate(name, linkedin, website, "https://example.test/gcc-energy",
                "Regional services player", 58);
    }

    private static CompanyRow marketRow(String apolloAccountId, String name) {
        return new CompanyRow(apolloAccountId, name, "utilities", "United Arab Emirates", "Abu Dhabi",
                3400, 5_000_000_000L, "https://" + apolloAccountId + ".example", null, null, 1998,
                null, null, null, null, null, null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(), List.of());
    }

    private static CapturedCompanyDetails vendorFacts() {
        return new CapturedCompanyDetails("Nebras Power", "utilities", "Qatar", "Doha", 410,
                800_000_000L, "https://nebras.example",
                "https://www.linkedin.com/company/nebras-power", 2014, null, null, null, null);
    }
}
