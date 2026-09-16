package app.lightmove.api.candidate.model;

import java.util.List;

/**
 * What a contact lookup answered for the phone channel, shaped like {@link FoundEmails}.
 *
 * <p>Numbers are kept exactly as the provider spelled them. Providers return a mix of E.164, national
 * and dashed forms, and normalising without knowing the country would turn a number that dials into
 * one that does not.
 */
public record FoundPhones(String source, List<String> phones) {

    public FoundPhones {
        phones = phones == null ? List.of() : List.copyOf(phones);
    }

    public static FoundPhones none(String source) {
        return new FoundPhones(source, List.of());
    }

    public String primary() {
        return phones.isEmpty() ? null : phones.getFirst();
    }
}
