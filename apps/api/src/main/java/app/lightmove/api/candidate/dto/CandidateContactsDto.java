package app.lightmove.api.candidate.dto;

import java.time.Instant;
import java.util.List;

/**
 * Every email and phone known for one executive, in the order the drawer lists them, and when each
 * channel was last asked.
 *
 * <p>The drawer reads the timestamps rather than the lists to decide what to offer: a null one means
 * the button is still worth pressing, and a set one with nothing from the provider means it had
 * nothing and pressing again would only buy the same answer. {@code source} names the provider that
 * answered, for the "Found via" line.
 */
public record CandidateContactsDto(List<CandidateEmailDto> emails, List<CandidatePhoneDto> phones,
                                   Instant emailsLookedUpAt, Instant phonesLookedUpAt, String source) {}
