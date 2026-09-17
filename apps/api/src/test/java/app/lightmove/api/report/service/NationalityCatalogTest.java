package app.lightmove.api.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** One heading per group however a researcher or a spreadsheet spelled it, and the Gulf line drawn once. */
class NationalityCatalogTest {

    @Test
    @DisplayName("a demonym, a country name and an abbreviation all count under one Gulf heading")
    void gulfSpellingsFold() {
        assertThat(NationalityCatalog.groupOf("saudi arabian")).isEqualTo("Saudi");
        assertThat(NationalityCatalog.groupOf(" Saudi ")).isEqualTo("Saudi");
        assertThat(NationalityCatalog.groupOf("KSA")).isEqualTo("Saudi");
        assertThat(NationalityCatalog.groupOf("UAE")).isEqualTo("Emirati");
    }

    @Test
    @DisplayName("everyone else is counted in one of three expat groups, by demonym or by country")
    void expatsFoldIntoTheirGroup() {
        assertThat(NationalityCatalog.groupOf("Egyptian")).isEqualTo("Arab expat, non-GCC");
        assertThat(NationalityCatalog.groupOf("Egypt")).isEqualTo("Arab expat, non-GCC");
        assertThat(NationalityCatalog.groupOf("indian")).isEqualTo("South Asian");
        assertThat(NationalityCatalog.groupOf("Pakistan")).isEqualTo("South Asian");
        assertThat(NationalityCatalog.groupOf("dutch")).isEqualTo("Western expat");
        assertThat(NationalityCatalog.groupOf("United Kingdom")).isEqualTo("Western expat");
    }

    @Test
    @DisplayName("a group picked in the drawer is counted under its own spelling, whatever its case")
    void groupLabelsRoundTrip() {
        assertThat(NationalityCatalog.groupOf("Arab expat, non-GCC")).isEqualTo("Arab expat, non-GCC");
        assertThat(NationalityCatalog.groupOf("ARAB EXPAT, NON-GCC")).isEqualTo("Arab expat, non-GCC");
        assertThat(NationalityCatalog.groupOf("western expat")).isEqualTo("Western expat");
        assertThat(NationalityCatalog.groupOf("South Asian")).isEqualTo("South Asian");
    }

    @Test
    @DisplayName("a spelling no group places keeps its own, title-cased, and a blank is nothing")
    void unplacedSpellingsAreKept() {
        assertThat(NationalityCatalog.groupOf("turkish")).isEqualTo("Turkish");
        assertThat(NationalityCatalog.groupOf("south african")).isEqualTo("South African");
        assertThat(NationalityCatalog.groupOf("  ")).isNull();
        assertThat(NationalityCatalog.groupOf(null)).isNull();
    }

    @Test
    @DisplayName("the six Gulf nationalities are the localisation line")
    void gulfNationalsAreNamed() {
        assertThat(NationalityCatalog.isGcc("Saudi")).isTrue();
        assertThat(NationalityCatalog.isGcc("Bahraini")).isTrue();
        assertThat(NationalityCatalog.isGcc("Arab expat, non-GCC")).isFalse();
        assertThat(NationalityCatalog.isGcc(null)).isFalse();
    }
}
