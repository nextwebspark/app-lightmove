package app.lightmove.api.enrichment.company.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.enrichment.company.model.VendorCompanyRecord;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A remembered company name against the shorter name its LinkedIn page carries. */
class CompanyNamesTest {

    @Test
    @DisplayName("a name is searched without its legal form first, then without its generic words")
    void searchesTheCoreAfterTheName() {
        assertThat(CompanyNames.searchTerms("Emaar Properties PJSC")).containsExactly("emaar properties", "emaar");
        assertThat(CompanyNames.searchTerms("Sobha Realty")).containsExactly("sobha realty");
        assertThat(CompanyNames.searchTerms("Rekaz Al Dar Properties L.L.C")).containsExactly(
                "rekaz al dar properties", "rekaz al dar");
    }

    @Test
    @DisplayName("the page named exactly as asked wins over a bigger namesake that only contains it")
    void picksTheExactName() {
        List<VendorCompanyRecord> hits = List.of(page("aldar-education", "Aldar Education", 12_000),
                page("aldar_properties", "ALDAR", 9_714));

        assertThat(CompanyNames.best("Aldar Properties", hits))
                .map(VendorCompanyRecord::linkedinSlug).contains("aldar_properties");
    }

    @Test
    @DisplayName("two pages with the asked name go to the bigger one")
    void breaksATieOnHeadcount() {
        List<VendorCompanyRecord> hits = List.of(page("nakheel-mall", "Nakheel", 60),
                page("nakheelofficial", "Nakheel", 1_893));

        assertThat(CompanyNames.best("Nakheel PJSC", hits))
                .map(VendorCompanyRecord::linkedinSlug).contains("nakheelofficial");
    }

    @Test
    @DisplayName("a page that only contains the name is no match, so a guessed name finds nothing")
    void refusesAPartialMatch() {
        List<VendorCompanyRecord> hits = List.of(page("yasplazahotels", "Yas Plaza Hotels by Aldar Hospitality", 404));

        assertThat(CompanyNames.best("Aldar", hits)).isEmpty();
    }

    private static VendorCompanyRecord page(String slug, String name, int employees) {
        return new VendorCompanyRecord(slug, name, "Real Estate", "United Arab Emirates", "Abu Dhabi",
                employees, null, "https://www.linkedin.com/company/" + slug, null, null, null, List.of(), "{}");
    }
}
