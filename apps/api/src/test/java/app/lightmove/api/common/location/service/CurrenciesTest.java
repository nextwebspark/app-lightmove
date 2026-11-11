package app.lightmove.api.common.location.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.common.location.model.OfferedCurrency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CurrenciesTest {

    @Test
    @DisplayName("a firm starts in its country's currency, however the country is spelled")
    void startsInTheCountrysCurrency() {
        assertThat(Currencies.startingCurrencyFor("Saudi Arabia")).isEqualTo("SAR");
        assertThat(Currencies.startingCurrencyFor("UAE")).isEqualTo("AED");
        assertThat(Currencies.startingCurrencyFor("United Kingdom")).isEqualTo("GBP");
        assertThat(Currencies.startingCurrencyFor("Germany")).isEqualTo("EUR");
        assertThat(Currencies.startingCurrencyFor("India")).isEqualTo("INR");
    }

    @Test
    @DisplayName("with no country, or one without a currency, the firm starts in AED")
    void fallsBackToTheDirham() {
        assertThat(Currencies.startingCurrencyFor(null)).isEqualTo("AED");
        assertThat(Currencies.startingCurrencyFor("Atlantis")).isEqualTo("AED");
        assertThat(Currencies.startingCurrencyFor("Antarctica")).isEqualTo("AED");
    }

    @Test
    @DisplayName("every currency in use today is offered once, named, and no retired one is")
    void offersTheCurrenciesInUse() {
        assertThat(Currencies.offered()).extracting(OfferedCurrency::code)
                .contains("AED", "SAR", "QAR", "KWD", "BHD", "OMR", "USD", "GBP", "EUR", "INR")
                .doesNotContain("ATS", "DEM", "XXX", "XAU")
                .doesNotHaveDuplicates()
                .isSorted();
        assertThat(Currencies.offered()).allSatisfy(currency -> assertThat(currency.name()).isNotBlank());
    }

    @Test
    @DisplayName("a code is known when ISO issues it, a retired one included, and never in lower case or as a metal")
    void knowsTheIsoCodes() {
        assertThat(Currencies.isKnownCode("SAR")).isTrue();
        assertThat(Currencies.isKnownCode("ATS")).isTrue();
        assertThat(Currencies.isKnownCode("sar")).isFalse();
        assertThat(Currencies.isKnownCode("XAU")).isFalse();
        assertThat(Currencies.isKnownCode("ZZZ")).isFalse();
        assertThat(Currencies.isKnownCode(null)).isFalse();
    }
}
