package app.lightmove.api.candidate.dto;

import app.lightmove.api.common.location.constant.PhoneCountrySource;
import app.lightmove.api.common.location.constant.PhoneLineType;
import java.time.Instant;

/**
 * One number the mandate knows, spelled as it arrived, and the door it came through; the international fields are
 * null where the number cannot be read as one (docs/candidate-crm.md, "Phones as the button").
 */
public record CandidatePhoneDto(String number, String kind, boolean verified, String status,
                                String source, Instant foundAt, boolean primary,
                                String international, String e164, String callingCode,
                                PhoneCountrySource countrySource, PhoneLineType lineType) {}
