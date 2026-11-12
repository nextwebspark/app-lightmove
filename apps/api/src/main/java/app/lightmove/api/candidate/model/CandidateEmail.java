package app.lightmove.api.candidate.model;

import static app.lightmove.api.core.text.service.SuppliedText.blankToNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Locale;

/**
 * One address a lookup found: {@code kind} work or personal and {@code status} the provider's word, each null where
 * it did not say. Strings, not enums, so an unseen value from the jsonb lands in the drawer instead of breaking it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CandidateEmail(String address, String kind, String status) {

    public static final String WORK = "work";
    public static final String PERSONAL = "personal";
    public static final String VERIFIED = "Verified";

    public CandidateEmail {
        address = blankToNull(address);
        if (address != null) {
            address = address.toLowerCase(Locale.ROOT);
        }
        kind = blankToNull(kind);
        status = blankToNull(status);
    }

    public boolean isVerified() {
        return VERIFIED.equalsIgnoreCase(status);
    }
}
