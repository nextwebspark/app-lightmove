package app.lightmove.api.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.ApolloUniverse;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Matching a company by the one identifier a web search and a vendor both return. The rule is
 * {@code matchEmployer}'s: unique or nothing, and never fuzzy.
 */
@IntegrationTest
class ApolloDomainMatchIntegrationTest {

    @Autowired JdbcTemplate db;
    @Autowired ApolloCompanyQueryService companies;

    private ApolloUniverse universe;

    @BeforeEach
    void freshUniverse() {
        universe = new ApolloUniverse(db);
        universe.reset();
    }

    @Test
    @DisplayName("a homepage finds its company however either side spells the URL")
    void aHomepageFindsItsCompany() {
        universe.company("a1", "ACWA Power").website("https://acwapower.example").insert();

        // Scheme, www and a path on one side, none of them on the other: WebsiteDomain decides, and
        // it is the same function on both.
        assertThat(named(companies.matchByDomain("https://www.acwapower.example/en/about")))
                .isEqualTo("ACWA Power");
        assertThat(named(companies.matchByDomain("acwapower.example"))).isEqualTo("ACWA Power");
    }

    @Test
    @DisplayName("a domain the LIKE would catch but the parse would not is no match")
    void aSubstringIsNotAMatch() {
        universe.company("a1", "ACWA Power").website("https://acwapower.example").insert();

        // '%acwapower.example%' matches this row in SQL; the Java equality is what refuses it.
        assertThat(companies.matchByDomain("https://notacwapower.example.test")).isEmpty();
    }

    @Test
    @DisplayName("two companies on one domain answer nothing, not the first")
    void anAmbiguousDomainMatchesNothing() {
        universe.company("a1", "Meridian Energy").website("https://meridian.example").insert();
        universe.company("a2", "Meridian Energy Services").website("https://www.meridian.example/uk").insert();

        assertThat(companies.matchByDomain("https://meridian.example")).isEmpty();
    }

    @Test
    @DisplayName("nothing to key on is no match rather than a scan")
    void nothingToKeyOnIsNoMatch() {
        universe.company("a1", "ACWA Power").website("https://acwapower.example").insert();

        assertThat(companies.matchByDomain(null)).isEmpty();
        assertThat(companies.matchByDomain("  ")).isEmpty();
        // Parses to no host, so there is no domain to ask about.
        assertThat(companies.matchByDomain("not a url at all")).isEmpty();
    }

    private static String named(Optional<CompanyRow> row) {
        return row.map(CompanyRow::companyName).orElse(null);
    }
}
