package app.lightmove.api.common.location.model;

import app.lightmove.api.common.location.constant.PhoneCountrySource;
import app.lightmove.api.common.location.constant.PhoneLineType;

/**
 * A written number read as an international one, spelled the international way ({@code +971 50 441 2087}: the
 * calling code, then the national groups), where its country code came from, and what kind of line it rings.
 */
public record InternationalPhone(String international, PhoneCountrySource countrySource, PhoneLineType lineType) {}
