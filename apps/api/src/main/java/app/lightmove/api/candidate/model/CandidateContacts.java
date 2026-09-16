package app.lightmove.api.candidate.model;

import static app.lightmove.api.core.text.service.SuppliedText.blankToNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * What a contact lookup found for one executive, and when it last ran.
 *
 * <p>The two timestamps are what keeps a lookup from being billed twice. A null one means the channel
 * has never been asked; a set one with an empty list means it was asked and the provider had nothing —
 * a miss worth remembering, because re-asking would spend a credit to be told the same thing.
 *
 * <p>ISO-8601 strings rather than {@code Instant}s for {@link CandidateProfile}'s reason: the jsonb
 * mapper is a bare Jackson 2 {@code ObjectMapper} with no time module, and a type it cannot read back
 * would make every profile carrying contacts unreadable.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CandidateContacts(List<CandidateEmail> emails, List<String> phones,
                                String emailsLookedUpAt, String phonesLookedUpAt, String source) {

    public CandidateContacts {
        emails = emails == null ? List.of()
                : emails.stream().filter(email -> email.address() != null).toList();
        phones = phones == null ? List.of() : List.copyOf(phones);
        emailsLookedUpAt = blankToNull(emailsLookedUpAt);
        phonesLookedUpAt = blankToNull(phonesLookedUpAt);
        source = blankToNull(source);
    }

    public static CandidateContacts none() {
        return new CandidateContacts(List.of(), List.of(), null, null, null);
    }

    public boolean hasAskedForEmails() {
        return emailsLookedUpAt != null;
    }

    public boolean hasAskedForPhones() {
        return phonesLookedUpAt != null;
    }

    public CandidateContacts withEmails(List<CandidateEmail> found, String foundSource, String at) {
        return new CandidateContacts(found, phones, at, phonesLookedUpAt,
                foundSource == null ? source : foundSource);
    }

    public CandidateContacts withPhones(List<String> found, String foundSource, String at) {
        return new CandidateContacts(emails, found, emailsLookedUpAt, at,
                foundSource == null ? source : foundSource);
    }
}
