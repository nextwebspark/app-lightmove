package app.lightmove.api.candidate.dto;

import java.time.Instant;

/**
 * One number the mandate knows, spelled as it arrived, and the door it came through. {@code kind}
 * is set only where a person tagged it: no provider says whether a number is a mobile or a desk. {@code primary} as
 * on {@link CandidateEmailDto}. {@code international} is the number spelled the international way as it reads at this
 * moment ({@code +971 50 441 2087}), its country code written or added from where the person or the firm is
 * ({@code countrySource}), null where it cannot be read;
 * {@code lineType} is mobile, landline or unknown, and null with it.
 */
public record CandidatePhoneDto(String number, String kind, boolean verified, String status,
                                String source, Instant foundAt, boolean primary,
                                String international, String countrySource, String lineType) {}
