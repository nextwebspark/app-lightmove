package app.lightmove.api.common.location.model;

import app.lightmove.api.common.location.constant.PhoneCountrySource;
import app.lightmove.api.common.location.constant.PhoneLineType;

/**
 * A written number read as an international one: spelled for reading ({@code +971 50 441 2087}), for dialling
 * ({@code +971504412087}), its calling code ({@code +971}), where that code came from, and what kind of line it rings.
 */
public record InternationalPhone(String international, String e164, String callingCode,
                                 PhoneCountrySource countrySource, PhoneLineType lineType) {}
