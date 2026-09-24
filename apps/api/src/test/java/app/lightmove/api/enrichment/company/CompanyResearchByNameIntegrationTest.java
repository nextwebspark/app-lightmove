package app.lightmove.api.enrichment.company;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingCompanyEnricher;
import app.lightmove.api.enrichment.company.model.VendorCompanyRecord;
import app.lightmove.api.enrichment.company.model.VendorSearchAllowance;
import app.lightmove.api.enrichment.company.service.CompanyResearch;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** A company found by name is bought once, and so is every namesake the same search paid for. */
@IntegrationTest
class CompanyResearchByNameIntegrationTest {

    @Autowired private CompanyResearch research;
    @Autowired private RecordingCompanyEnricher enricher;
    @Autowired private JdbcTemplate db;

    @BeforeEach
    void freshCache() {
        enricher.clear();
        db.update("DELETE FROM app_lm_vendor_company WHERE linkedin_slug IN ('aldar_properties', 'aldar-education', 'majid-al-futtaim', 'tiny-ikea')");
    }

    @Test
    @DisplayName("a name is answered with the page's full facts, and asked again it costs nothing")
    void remembersTheCompanyFound() {
        enricher.answerSearchWith(List.of(page("aldar_properties", "ALDAR", 9_714),
                page("aldar-education", "Aldar Education", 2_115)));

        CapturedCompanyDetails first = research.byName("Aldar Properties PJSC", "United Arab Emirates", plenty()).orElseThrow();
        CapturedCompanyDetails again = research.byName("Aldar Properties", "UAE", plenty()).orElseThrow();

        assertThat(first.companyName()).isEqualTo("ALDAR");
        assertThat(first.numEmployees()).isEqualTo(9_714);
        assertThat(first.companyLinkedinUrl()).contains("aldar_properties");
        assertThat(again.companyName()).isEqualTo("ALDAR");
        assertThat(enricher.searchedNames()).containsExactly("aldar properties");
    }

    @Test
    @DisplayName("a namesake the first search returned is read from the cache, not bought again")
    void remembersEveryHitPaidFor() {
        enricher.answerSearchWith(List.of(page("aldar_properties", "ALDAR", 9_714),
                page("aldar-education", "Aldar Education", 2_115)));
        research.byName("Aldar Properties", "United Arab Emirates", plenty());

        CapturedCompanyDetails education = research.byName("Aldar Education", "United Arab Emirates", plenty()).orElseThrow();

        assertThat(education.numEmployees()).isEqualTo(2_115);
        assertThat(enricher.searchedNames()).containsExactly("aldar properties");
    }

    @Test
    @DisplayName("a name no page carries finds nothing")
    void findsNothingForAnUnknownName() {
        enricher.answerSearchWith(List.of());

        assertThat(research.byName("Gulf Horizon Realty", "United Arab Emirates", plenty())).isEmpty();
        assertThat(enricher.searchedNames()).containsExactly("gulf horizon realty");
    }

    @Test
    @DisplayName("a page named with its legal form is found again by the name without it, at no cost")
    void findsALegalFormPageAgain() {
        enricher.answerSearchWith(List.of(page("majid-al-futtaim", "Majid Al Futtaim LLC", 27_600)));

        research.byName("Majid Al Futtaim", "United Arab Emirates", plenty()).orElseThrow();
        CapturedCompanyDetails again = research.byName("Majid Al Futtaim", "United Arab Emirates", plenty())
                .orElseThrow();

        assertThat(again.companyName()).isEqualTo("Majid Al Futtaim LLC");
        assertThat(enricher.searchedNames()).containsExactly("majid al futtaim");
    }

    @Test
    @DisplayName("a small namesake cached by a local search is never served as a global company")
    void keepsTheGlobalFloorOnACacheHit() {
        enricher.answerSearchWith(List.of(page("tiny-ikea", "IKEA", 60)));
        research.byName("IKEA", "United Arab Emirates", plenty());
        enricher.answerSearchWith(List.of());

        assertThat(research.byNameAnywhere("IKEA", plenty())).isEmpty();
        assertThat(enricher.searchedNames()).containsExactly("ikea", "ikea");
    }

    @Test
    @DisplayName("no search is made once the answer's allowance is spent")
    void stopsAtTheAllowance() {
        enricher.answerSearchWith(List.of());
        VendorSearchAllowance one = new VendorSearchAllowance(1);

        research.byName("Emaar Properties", "United Arab Emirates", one);

        assertThat(enricher.searchedNames()).containsExactly("emaar properties");
        assertThat(one.used()).isEqualTo(1);
    }

    private static VendorSearchAllowance plenty() {
        return new VendorSearchAllowance(20);
    }

    private static VendorCompanyRecord page(String slug, String name, int employees) {
        return new VendorCompanyRecord(slug, name, "Real Estate", "United Arab Emirates", "Abu Dhabi",
                employees, "https://" + slug + ".example", "https://www.linkedin.com/company/" + slug,
                2005, "A developer.", null, List.of("real estate"), "{}");
    }
}
