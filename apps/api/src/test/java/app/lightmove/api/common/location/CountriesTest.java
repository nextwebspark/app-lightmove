package app.lightmove.api.common.location;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.common.location.model.Country;
import app.lightmove.api.common.location.model.LocationLine;
import app.lightmove.api.common.location.service.Countries;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CountriesTest {

    @Test
    @DisplayName("every spelling of one country resolves to one code and one name")
    void spellingsFold() {
        assertThat(Countries.resolve("UAE")).contains(new Country("AE", "United Arab Emirates"));
        assertThat(Countries.resolve("  u.a.e. ")).contains(new Country("AE", "United Arab Emirates"));
        assertThat(Countries.resolve("emirates")).contains(new Country("AE", "United Arab Emirates"));
        assertThat(Countries.resolve("AE")).contains(new Country("AE", "United Arab Emirates"));
        assertThat(Countries.resolve("KSA")).contains(new Country("SA", "Saudi Arabia"));
        // The name the universe spells it, and the name it used to be spelled.
        assertThat(Countries.nameOf("Türkiye")).isEqualTo("Türkiye");
        assertThat(Countries.nameOf("Turkey")).isEqualTo("Türkiye");
    }

    @Test
    @DisplayName("the eight markets the universe carries are spelled as the universe spells them")
    void marketNamesMatchTheUniverse() {
        // A canonical name that disagreed with app_lm_apollo_companies.company_country would make the
        // Strategy filter, which matches that column exactly, silently miss every canonicalised row.
        assertThat(Countries.nameOf("united arab emirates")).isEqualTo("United Arab Emirates");
        assertThat(Countries.nameOf("saudi arabia")).isEqualTo("Saudi Arabia");
        assertThat(Countries.nameOf("egypt")).isEqualTo("Egypt");
        assertThat(Countries.nameOf("qatar")).isEqualTo("Qatar");
        assertThat(Countries.nameOf("kuwait")).isEqualTo("Kuwait");
        assertThat(Countries.nameOf("oman")).isEqualTo("Oman");
        assertThat(Countries.nameOf("bahrain")).isEqualTo("Bahrain");
    }

    @Test
    @DisplayName("a bare code is a country where a picker sent it and never in a line of prose")
    void bareCodesResolveOnlyWhereTheyAreMeant() {
        // The two catalogs differ by this one pass and nothing else, so they are built one from the
        // other rather than twice from scratch. A drift here is what filed Chicago under Israel.
        assertThat(Countries.resolve("ae")).contains(new Country("AE", "United Arab Emirates"));
        assertThat(Countries.resolveSpelling("ae")).isEmpty();
        // Everything a spelled-out name or an alias reaches is reachable from both.
        assertThat(Countries.resolveSpelling("UAE")).contains(new Country("AE", "United Arab Emirates"));
        assertThat(Countries.resolveSpelling("United Arab Emirates"))
                .contains(new Country("AE", "United Arab Emirates"));
    }

    @Test
    @DisplayName("a name the catalog does not know is kept, not dropped")
    void unknownIsKept() {
        assertThat(Countries.resolve("Atlantis")).isEmpty();
        assertThat(Countries.nameOf("  Atlantis ")).isEqualTo("Atlantis");
        assertThat(Countries.nameOf(null)).isNull();
        assertThat(Countries.nameOf("   ")).isNull();
    }

    @Test
    @DisplayName("a code reads back as one English name")
    void codesReadBack() {
        assertThat(Countries.nameOfCode("AE")).contains("United Arab Emirates");
        assertThat(Countries.nameOfCode("ZZ")).isEmpty();
        assertThat(Countries.nameOfCode(null)).isEmpty();
    }

    @Test
    @DisplayName("a city keeps its own spelling unless the catalog holds a canonical one")
    void citiesFoldWhereKnown() {
        assertThat(Countries.cityOf("khobar")).isEqualTo("Al Khobar");
        assertThat(Countries.cityOf("  DUBAI ")).isEqualTo("Dubai");
        assertThat(Countries.cityOf("Nowheresville")).isEqualTo("Nowheresville");
    }

    @Test
    @DisplayName("a vendor's one-line location splits into the two halves the schema stores")
    void linesSplit() {
        assertThat(LocationLine.of("Dubai, United Arab Emirates"))
                .isEqualTo(new LocationLine("Dubai", "United Arab Emirates"));
        // Every segment before the country is city — the vendor's own repetition is not ours to edit.
        assertThat(LocationLine.of("Dubai, Dubai, UAE"))
                .isEqualTo(new LocationLine("Dubai, Dubai", "United Arab Emirates"));
        // No country on the end: all city. "Greater Dubai Area" is a place, not a mistake.
        assertThat(LocationLine.of("Greater Dubai Area"))
                .isEqualTo(new LocationLine("Greater Dubai Area", null));
        // A line that is only a country is that country, with no city invented for it.
        assertThat(LocationLine.of("United Arab Emirates"))
                .isEqualTo(new LocationLine(null, "United Arab Emirates"));
        assertThat(LocationLine.of(null)).isEqualTo(new LocationLine(null, null));
    }

    @Test
    @DisplayName("a state abbreviation on the end of a line is not a country")
    void stateCodesAreNotCountries() {
        // 26 US state and Canadian province codes are also ISO country codes. Reading the tail as one
        // filed Chicago under Israel, San Francisco under Canada and Boston under Morocco.
        assertThat(LocationLine.of("Chicago, IL")).isEqualTo(new LocationLine("Chicago", null));
        assertThat(LocationLine.of("San Francisco, CA")).isEqualTo(new LocationLine("San Francisco", null));
        assertThat(LocationLine.of("Boston, MA")).isEqualTo(new LocationLine("Boston", null));
        // A written-out name or one of the catalog's own abbreviations still reads as a country.
        assertThat(LocationLine.of("London, UK")).isEqualTo(new LocationLine("London", "United Kingdom"));
        assertThat(LocationLine.of("Dubai, UAE"))
                .isEqualTo(new LocationLine("Dubai", "United Arab Emirates"));
    }

    @Test
    @DisplayName("a vendor's own country code outranks a country read out of prose")
    void vendorCodeWins() {
        // Bright Data sends country_codes_array beside a free-text headquarters line. The code is the
        // better evidence, and letting the line win wrote the wrong country over a correct one.
        assertThat(LocationLine.of("Chicago, Illinois").countryOr("US")).isEqualTo("United States");
        assertThat(LocationLine.of("Dubai, United Arab Emirates").countryOr(null))
                .isEqualTo("United Arab Emirates");
    }

    @Test
    @DisplayName("every city before the country is kept, not just the first")
    void middleSegmentsSurvive() {
        // A brief may name two cities, and a vendor writes district before city before country.
        assertThat(LocationLine.of("Sandton, Johannesburg, South Africa"))
                .isEqualTo(new LocationLine("Sandton, Johannesburg", "South Africa"));
    }

    @Test
    @DisplayName("a line of nothing but separators is empty, not a crash")
    void straySeparatorsSurvive() {
        // ",".split(",") is an empty array, so reading its last element threw. Reachable from the
        // brief's free-text Location field and from any vendor's location line.
        assertThat(LocationLine.of(",")).isEqualTo(new LocationLine(null, null));
        assertThat(LocationLine.of(" , , ")).isEqualTo(new LocationLine(null, null));
        assertThat(LocationLine.of("   ")).isEqualTo(new LocationLine(null, null));
        // And a trailing separator is not part of the city's name.
        assertThat(LocationLine.of("Dubai,")).isEqualTo(new LocationLine("Dubai", null));
    }

    @Test
    @DisplayName("a job location a picker could not express survives being read")
    void freeTextLocationsSurvive() {
        assertThat(LocationLine.of("Remote")).isEqualTo(new LocationLine("Remote", null));
        assertThat(LocationLine.of("Abu Dhabi")).isEqualTo(new LocationLine("Abu Dhabi", null));
        // A tail that is not a country leaves the line as a city, first segment first.
        assertThat(LocationLine.of("San Francisco, California"))
                .isEqualTo(new LocationLine("San Francisco", null));
    }
}
