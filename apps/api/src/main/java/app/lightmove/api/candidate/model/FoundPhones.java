package app.lightmove.api.candidate.model;

import java.util.List;
import org.springframework.modulith.NamedInterface;

/**
 * What a contact lookup answered for the phone channel, shaped like {@link FoundEmails}.
 *
 * <p>Numbers are kept exactly as the provider spelled them. Providers return a mix of E.164, national
 * and dashed forms, and normalising without knowing the country would turn a number that dials into
 * one that does not.
 */
@NamedInterface(name = "api", propagate = false)
public record FoundPhones(String source, List<String> phones) {

    public FoundPhones {
        phones = phones == null ? List.of() : List.copyOf(phones);
    }

    public static FoundPhones none(String source) {
        return new FoundPhones(source, List.of());
    }
}
