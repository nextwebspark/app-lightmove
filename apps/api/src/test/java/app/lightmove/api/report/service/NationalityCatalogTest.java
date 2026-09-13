package app.lightmove.api.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** One heading per nationality however a researcher spelled it, and the Gulf line drawn once. */
class NationalityCatalogTest {

    @Test
    @DisplayName("a demonym, a country name and an abbreviation all count under one heading")
    void spellingsFold() {
        assertThat(NationalityCatalog.demonymOf("saudi arabian")).isEqualTo("Saudi");
        assertThat(NationalityCatalog.demonymOf(" Saudi ")).isEqualTo("Saudi");
        assertThat(NationalityCatalog.demonymOf("KSA")).isEqualTo("Saudi");
        assertThat(NationalityCatalog.demonymOf("Egypt")).isEqualTo("Egyptian");
        assertThat(NationalityCatalog.demonymOf("UAE")).isEqualTo("Emirati");
    }

    @Test
    @DisplayName("an unknown spelling keeps its own, title-cased, and a blank is nothing")
    void unknownSpellingsAreKept() {
        assertThat(NationalityCatalog.demonymOf("dutch")).isEqualTo("Dutch");
        assertThat(NationalityCatalog.demonymOf("new zealander")).isEqualTo("New Zealander");
        assertThat(NationalityCatalog.demonymOf("  ")).isNull();
        assertThat(NationalityCatalog.demonymOf(null)).isNull();
    }

    @Test
    @DisplayName("the six Gulf nationalities are the localisation line")
    void gulfNationalsAreNamed() {
        assertThat(NationalityCatalog.isGcc("Saudi")).isTrue();
        assertThat(NationalityCatalog.isGcc("Bahraini")).isTrue();
        assertThat(NationalityCatalog.isGcc("Egyptian")).isFalse();
        assertThat(NationalityCatalog.isGcc(null)).isFalse();
    }
}
