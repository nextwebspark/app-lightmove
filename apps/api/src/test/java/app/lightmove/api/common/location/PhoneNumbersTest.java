package app.lightmove.api.common.location;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.common.location.constant.PhoneCountrySource;
import app.lightmove.api.common.location.constant.PhoneLineType;
import app.lightmove.api.common.location.model.InternationalPhone;
import app.lightmove.api.common.location.service.PhoneNumbers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhoneNumbersTest {

    @Test
    @DisplayName("a number written with its country code keeps it, spelled the international way however it was written")
    void givenCountryCodeIsKept() {
        InternationalPhone expected = new InternationalPhone("+971 50 441 2087", "+971504412087", "+971",
                PhoneCountrySource.GIVEN, PhoneLineType.MOBILE);
        assertThat(PhoneNumbers.internationalOf("+971 50 441 2087", "Saudi Arabia", null)).contains(expected);
        assertThat(PhoneNumbers.internationalOf("00971504412087", null, null)).contains(expected);
        assertThat(PhoneNumbers.internationalOf("+966 (55) 290-1146", null, null))
                .contains(new InternationalPhone("+966 55 290 1146", "+966552901146", "+966",
                        PhoneCountrySource.GIVEN, PhoneLineType.MOBILE));
        assertThat(PhoneNumbers.internationalOf("+974 5512 8870 ext 12", null, null))
                .map(InternationalPhone::international).contains("+974 5512 8870");
    }

    @Test
    @DisplayName("a local number takes the person's country, in any spelling the catalog knows")
    void localNumberTakesThePersonsCountry() {
        assertThat(PhoneNumbers.internationalOf("050 441 2087", "United Arab Emirates", "Qatar"))
                .contains(new InternationalPhone("+971 50 441 2087", "+971504412087", "+971",
                        PhoneCountrySource.LOCATION, PhoneLineType.MOBILE));
        assertThat(PhoneNumbers.internationalOf("055 290 1146", "KSA", null))
                .map(InternationalPhone::international).contains("+966 55 290 1146");
        assertThat(PhoneNumbers.internationalOf("5512 8870", "Qatar", null))
                .map(InternationalPhone::international).contains("+974 5512 8870");
    }

    @Test
    @DisplayName("a landline and a service number say so, so neither is offered WhatsApp; a US number may be either")
    void landlineAndServiceNumbersAreNamed() {
        assertThat(PhoneNumbers.internationalOf("04 330 0000", "UAE", null))
                .map(InternationalPhone::lineType).contains(PhoneLineType.LANDLINE);
        assertThat(PhoneNumbers.internationalOf("800 4321", "UAE", null))
                .map(InternationalPhone::lineType).contains(PhoneLineType.SERVICE);
        assertThat(PhoneNumbers.internationalOf("+1 800 555 0199", null, null))
                .map(InternationalPhone::lineType).contains(PhoneLineType.SERVICE);
        assertThat(PhoneNumbers.internationalOf("+1 415 555 2671", null, null))
                .map(InternationalPhone::lineType).contains(PhoneLineType.UNKNOWN);
    }

    @Test
    @DisplayName("a number not valid in the person's country is never given its code; the firm's is the last guess")
    void invalidHintFallsThrough() {
        assertThat(PhoneNumbers.internationalOf("07400 123456", "United Arab Emirates", null)).isEmpty();
        assertThat(PhoneNumbers.internationalOf("07400 123456", "United Arab Emirates", "United Kingdom"))
                .contains(new InternationalPhone("+44 7400 123456", "+447400123456", "+44",
                        PhoneCountrySource.WORKSPACE, PhoneLineType.MOBILE));
        assertThat(PhoneNumbers.internationalOf("050 441 2087", null, "United Arab Emirates"))
                .map(InternationalPhone::countrySource).contains(PhoneCountrySource.WORKSPACE);
    }

    @Test
    @DisplayName("nothing to read answers nothing")
    void unreadableAnswersNothing() {
        assertThat(PhoneNumbers.internationalOf(null, "UAE", "UAE")).isEmpty();
        assertThat(PhoneNumbers.internationalOf("   ", "UAE", "UAE")).isEmpty();
        assertThat(PhoneNumbers.internationalOf("call the office", "UAE", "UAE")).isEmpty();
        assertThat(PhoneNumbers.internationalOf("050 441 2087", "Atlantis", null)).isEmpty();
        assertThat(PhoneNumbers.internationalOf("+971 50", null, null)).isEmpty();
    }
}
