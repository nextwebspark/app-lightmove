package app.lightmove.api.candidate.dto;

import java.util.List;

/**
 * Everything a contact lookup found for one executive, and when each channel was last asked.
 *
 * <p>The drawer reads the timestamps rather than the lists to decide what to offer: a null one means
 * the button is still worth pressing, and a set one with an empty list means the provider had nothing
 * and pressing again would only buy the same answer.
 */
public record CandidateContactsDto(List<CandidateEmailDto> emails, List<String> phones,
                                   String emailsLookedUpAt, String phonesLookedUpAt, String source) {}
