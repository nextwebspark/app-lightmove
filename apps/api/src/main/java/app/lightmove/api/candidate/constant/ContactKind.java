package app.lightmove.api.candidate.constant;

import java.util.Locale;

/**
 * Work or personal, as the provider said it or as a person tagged it. An address nobody has tagged
 * has no kind at all: the application never guesses one from the domain.
 */
public enum ContactKind {

    WORK,
    PERSONAL;

    /** {@code "work"} / {@code "personal"} as the wire and the providers spell them; anything else is no kind. */
    public static ContactKind fromValue(String kind) {
        if (kind == null) {
            return null;
        }
        return switch (kind.trim().toLowerCase(Locale.ROOT)) {
            case "work" -> WORK;
            case "personal" -> PERSONAL;
            default -> null;
        };
    }

    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
