package app.lightmove.api.candidate.constant;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.Locale;

/**
 * Which door one contact value came through. The three row doors of {@link CandidateSource} plus the
 * lookup provider, because a value can arrive through a different door from the row that holds it: a
 * plugin-captured executive whose phone a researcher later typed, or found.
 *
 * <p>Stored by name, matching V54's CHECK — a new provider is a migration, not a label.
 */
public enum ContactSource {

    MANUAL,
    CSV,
    EXTENSION,
    CONTACTOUT;

    public static ContactSource ofDoor(CandidateSource door) {
        return switch (door) {
            case MANUAL -> MANUAL;
            case CSV -> CSV;
            case EXTENSION -> EXTENSION;
        };
    }

    /** The name a {@code ContactFinder} answers with, e.g. {@code "contactout"}. */
    public static ContactSource ofProvider(String provider) {
        if (provider == null || !CONTACTOUT.name().equalsIgnoreCase(provider.trim())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Unknown contact provider: " + provider);
        }
        return CONTACTOUT;
    }

    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
