package app.lightmove.api.common.location.service;

import app.lightmove.api.common.location.constant.PhoneCountrySource;
import app.lightmove.api.common.location.constant.PhoneLineType;
import app.lightmove.api.common.location.model.Country;
import app.lightmove.api.common.location.model.InternationalPhone;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import java.util.Optional;

/**
 * A number as it was written, read as an international one. A number written with its country code keeps it; one
 * written locally takes the person's country, then the firm's, and only where it is a valid number there, so a UK
 * mobile on someone living in Dubai is never given +971.
 */
public final class PhoneNumbers {

    private static final PhoneNumberUtil NUMBERS = PhoneNumberUtil.getInstance();

    private static final String NO_REGION = "ZZ";

    private PhoneNumbers() {
    }

    public static Optional<InternationalPhone> internationalOf(String written, String personCountry,
                                                               String workspaceCountry) {
        if (written == null || written.isBlank()) {
            return Optional.empty();
        }
        String number = written.strip();
        if (number.startsWith("00")) {
            number = "+" + number.substring(2);
        }
        if (number.startsWith("+")) {
            return validInternationalOf(number, NO_REGION, PhoneCountrySource.GIVEN);
        }
        String local = number;
        return regionOf(personCountry)
                .flatMap(region -> validInternationalOf(local, region, PhoneCountrySource.LOCATION))
                .or(() -> regionOf(workspaceCountry)
                        .flatMap(region -> validInternationalOf(local, region, PhoneCountrySource.WORKSPACE)));
    }

    private static Optional<InternationalPhone> validInternationalOf(String number, String region,
                                                                     PhoneCountrySource source) {
        try {
            PhoneNumber parsed = NUMBERS.parse(number, region).clearExtension();
            boolean valid = source == PhoneCountrySource.GIVEN
                    ? NUMBERS.isValidNumber(parsed)
                    : NUMBERS.isValidNumberForRegion(parsed, region);
            if (!valid) {
                return Optional.empty();
            }
            return Optional.of(new InternationalPhone(
                    NUMBERS.format(parsed, PhoneNumberFormat.INTERNATIONAL),
                    NUMBERS.format(parsed, PhoneNumberFormat.E164),
                    "+" + parsed.getCountryCode(),
                    source,
                    lineTypeOf(NUMBERS.getNumberType(parsed))));
        } catch (NumberParseException unreadable) {
            return Optional.empty();
        }
    }

    private static Optional<String> regionOf(String country) {
        return Countries.resolve(country).map(Country::code);
    }

    private static PhoneLineType lineTypeOf(PhoneNumberType type) {
        return switch (type) {
            case MOBILE -> PhoneLineType.MOBILE;
            case FIXED_LINE -> PhoneLineType.LANDLINE;
            case TOLL_FREE, PREMIUM_RATE, SHARED_COST, UAN, VOICEMAIL -> PhoneLineType.SERVICE;
            default -> PhoneLineType.UNKNOWN;
        };
    }
}
