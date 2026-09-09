package app.lightmove.api.geocoding.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CountryCodesTest {

    @Test
    @DisplayName("the six Gulf states as the Apollo export spells them, and as a researcher types them")
    void gulfStatesResolve() {
        assertThat(CountryCodes.isoCodeOf("United Arab Emirates")).contains("AE");
        assertThat(CountryCodes.isoCodeOf("  saudi arabia ")).contains("SA");
        assertThat(CountryCodes.isoCodeOf("Qatar")).contains("QA");
        assertThat(CountryCodes.isoCodeOf("Kuwait")).contains("KW");
        assertThat(CountryCodes.isoCodeOf("Oman")).contains("OM");
        assertThat(CountryCodes.isoCodeOf("Bahrain")).contains("BH");
        assertThat(CountryCodes.isoCodeOf("UAE")).contains("AE");
        assertThat(CountryCodes.isoCodeOf("KSA")).contains("SA");
        assertThat(CountryCodes.isoCodeOf("ae")).contains("AE");
    }

    @Test
    @DisplayName("an unknown name is an empty answer, not an exception")
    void unknownIsEmpty() {
        assertThat(CountryCodes.isoCodeOf("Atlantis")).isEmpty();
        assertThat(CountryCodes.isoCodeOf(null)).isEmpty();
        assertThat(CountryCodes.isoCodeOf("   ")).isEmpty();
    }
}
