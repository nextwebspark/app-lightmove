package app.lightmove.api.candidate.dto;

import java.time.Instant;
import java.util.List;

/**
 * Every email and phone known for one executive, in the drawer's order, and when each channel was last asked. The
 * timestamps decide what to offer: null is worth pressing; set with nothing found means asking again buys the same.
 */
public record CandidateContactsDto(List<CandidateEmailDto> emails, List<CandidatePhoneDto> phones,
                                   Instant emailsLookedUpAt, Instant phonesLookedUpAt, String source) {}
