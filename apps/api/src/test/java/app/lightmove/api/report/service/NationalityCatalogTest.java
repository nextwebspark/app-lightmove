package app.lightmove.api.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
    @DisplayName("everyone else is counted in one of five expat groups, by demonym or by country")
    void expatsFoldIntoTheirGroup() {
        assertThat(NationalityCatalog.groupOf("Egyptian")).isEqualTo("Arab expat, non-GCC");
        assertThat(NationalityCatalog.groupOf("Egypt")).isEqualTo("Arab expat, non-GCC");
        assertThat(NationalityCatalog.groupOf("indian")).isEqualTo("South Asian");
        assertThat(NationalityCatalog.groupOf("Pakistan")).isEqualTo("South Asian");
        assertThat(NationalityCatalog.groupOf("dutch")).isEqualTo("Western expat");
        assertThat(NationalityCatalog.groupOf("United Kingdom")).isEqualTo("Western expat");
        assertThat(NationalityCatalog.groupOf("polish")).isEqualTo("Western expat");
        assertThat(NationalityCatalog.groupOf("Romania")).isEqualTo("Western expat");
        assertThat(NationalityCatalog.groupOf("Bhutan")).isEqualTo("South Asian");
        assertThat(NationalityCatalog.groupOf("chinese")).isEqualTo("Asian");
        assertThat(NationalityCatalog.groupOf("Filipino")).isEqualTo("Asian");
        assertThat(NationalityCatalog.groupOf("Philippines")).isEqualTo("Asian");
        assertThat(NationalityCatalog.groupOf("turkish")).isEqualTo("Other expat");
        assertThat(NationalityCatalog.groupOf("Iran")).isEqualTo("Other expat");
    }

    @Test
    @DisplayName("any country the catalog resolves but no other group claims is Other expat")
    void unclaimedCountriesAreOtherExpat() {
        assertThat(NationalityCatalog.groupOf("Nigeria")).isEqualTo("Other expat");
        assertThat(NationalityCatalog.groupOf("Brazil")).isEqualTo("Other expat");
        assertThat(NationalityCatalog.groupOf("south african")).isEqualTo("Other expat");
    }

    @Test
    @DisplayName("a group picked in the drawer is counted under its own spelling, whatever its case")
    void groupLabelsRoundTrip() {
        assertThat(NationalityCatalog.groupOf("Arab expat, non-GCC")).isEqualTo("Arab expat, non-GCC");
        assertThat(NationalityCatalog.groupOf("ARAB EXPAT, NON-GCC")).isEqualTo("Arab expat, non-GCC");
        assertThat(NationalityCatalog.groupOf("western expat")).isEqualTo("Western expat");
        assertThat(NationalityCatalog.groupOf("South Asian")).isEqualTo("South Asian");
        assertThat(NationalityCatalog.groupOf("asian")).isEqualTo("Asian");
        assertThat(NationalityCatalog.groupOf("Other Expat")).isEqualTo("Other expat");
    }

    @Test
    @DisplayName("a spelling no group places keeps its own, title-cased, and a blank is nothing")
    void unplacedSpellingsAreKept() {
        assertThat(NationalityCatalog.groupOf("martian")).isEqualTo("Martian");
        assertThat(NationalityCatalog.groupOf("wakandan")).isEqualTo("Wakandan");
        assertThat(NationalityCatalog.groupOf("  ")).isNull();
        assertThat(NationalityCatalog.groupOf(null)).isNull();
    }

    @Test
    @DisplayName("there are eleven groups and no twelfth: a kept spelling is a label, not a group")
    void onlyTheElevenAreGroups() {
        assertThat(List.of("Saudi", "Emirati", "Qatari", "Kuwaiti", "Omani", "Bahraini", "Western expat",
                "South Asian", "Asian", "Arab expat, non-GCC", "Other expat")).allMatch(NationalityCatalog::isGroup);
        assertThat(NationalityCatalog.isGroup(NationalityCatalog.groupOf("martian"))).isFalse();
        assertThat(NationalityCatalog.isGroup("Other")).isFalse();
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
