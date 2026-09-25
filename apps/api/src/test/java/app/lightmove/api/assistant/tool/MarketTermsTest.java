package app.lightmove.api.assistant.tool;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.strategy.service.SectorTaxonomy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

/** That a country and an industry written the way people write them reach the universe's spelling. */
class MarketTermsTest {

    private final MarketTerms terms = new MarketTerms(new SectorTaxonomy(new ObjectMapper()));

    @ParameterizedTest
    @ValueSource(strings = {"UAE", "the U.A.E.", "Emirates", "AE", "united arab emirates"})
    @DisplayName("the Emirates is read however it is written")
    void readsTheEmirates(String spelling) {
        ResolvedMarketTerms resolved = terms.resolve(spelling, null);

        assertThat(resolved.countries()).containsExactly("United Arab Emirates");
        assertThat(resolved.isFullyRecognised()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"KSA", "Saudi", "Saudi-Arabia", "Kingdom of Saudi Arabia"})
    @DisplayName("Saudi Arabia is read however it is written")
    void readsSaudiArabia(String spelling) {
        assertThat(terms.resolve(spelling, null).countries()).containsExactly("Saudi Arabia");
    }

    @Test
    @DisplayName("the GCC is its six members, and the reading says so")
    void readsTheGccAsItsMembers() {
        ResolvedMarketTerms resolved = terms.resolve("the GCC", null);

        assertThat(resolved.countries()).containsExactlyInAnyOrder("United Arab Emirates", "Saudi Arabia",
                "Qatar", "Kuwait", "Bahrain", "Oman");
        assertThat(resolved.countryLabel()).isEqualTo("the GCC");
        assertThat(resolved.interpretedAs()).singleElement().asString().startsWith("the GCC → ");
    }

    @Test
    @DisplayName("the Middle East is the GCC and the markets beside it")
    void readsTheMiddleEast() {
        assertThat(terms.resolve("Middle East", null).countries())
                .hasSize(10)
                .contains("Qatar", "Jordan", "Lebanon", "Iraq", "Egypt");
    }

    @Test
    @DisplayName("an industry is read whatever its case, conjunction or trailing 'sector'")
    void readsAnIndustry() {
        assertThat(terms.resolve(null, "Retail").industries()).containsExactly("retail");
        assertThat(terms.resolve(null, "oil and gas").industries()).containsExactly("oil & energy");
        assertThat(terms.resolve(null, "banking sector").industries()).containsExactly("banking");
    }

    @Test
    @DisplayName("a sector is every industry filed under it")
    void readsASectorAsItsIndustries() {
        ResolvedMarketTerms resolved = terms.resolve(null, "technology");

        assertThat(resolved.industries()).hasSize(11).contains("information technology & services");
        assertThat(resolved.industryLabel()).isEqualTo("technology");
        assertThat(resolved.interpretedAs()).singleElement().asString().startsWith("Technology → ");
    }

    @Test
    @DisplayName("a name that is both an industry and a sector is the industry")
    void prefersTheIndustryOverTheSector() {
        assertThat(terms.resolve(null, "Financial Services").industries()).containsExactly("financial services");
    }

    @Test
    @DisplayName("what cannot be read is reported rather than guessed")
    void reportsWhatItCannotRead() {
        ResolvedMarketTerms resolved = terms.resolve("Levantia", "space mining");

        assertThat(resolved.countries()).isEmpty();
        assertThat(resolved.industries()).isEmpty();
        assertThat(resolved.unrecognised()).containsExactly("country \"Levantia\"", "industry \"space mining\"");
    }

    @Test
    @DisplayName("a blank argument is an omitted one")
    void treatsABlankArgumentAsOmitted() {
        ResolvedMarketTerms resolved = terms.resolve("  ", "");

        assertThat(resolved.countries()).isEmpty();
        assertThat(resolved.industries()).isEmpty();
        assertThat(resolved.isFullyRecognised()).isTrue();
    }
}
