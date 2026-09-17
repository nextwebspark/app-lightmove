package app.lightmove.api.candidate.model;

import static app.lightmove.api.core.text.service.SuppliedText.blankToNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Locale;

/**
 * One address a contact lookup found, with what the provider said about it.
 *
 * <p>{@code kind} is {@code "work"} or {@code "personal"} — null where the provider listed an address
 * without saying which — and {@code status} is the provider's own word for whether it verified the
 * address ({@code "Verified"}), null where it did not say. Both stay strings rather than enums: they
 * are read back out of a jsonb column, and a value we have not seen before must land in the drawer
 * rather than break the profile.
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

    public boolean isWork() {
        return WORK.equals(kind);
    }

    public boolean isVerified() {
        return VERIFIED.equalsIgnoreCase(status);
    }
}
