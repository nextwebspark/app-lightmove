package app.lightmove.api.subprocessor.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A new sub-processor Uncava announced to every workspace (V248). {@code publishedByName} is null once erased. */
public record SubprocessorNotice(
        UUID id,
        String provider,
        String receives,
        String purpose,
        String region,
        LocalDate effectiveOn,
        String listUrl,
        @Nullable String publishedByName,
        Instant publishedAt
) {

    /** From its effective date on, the change has happened: nobody is emailed about it and nobody may object. */
    public boolean isInEffectOn(LocalDate day) {
        return !day.isBefore(effectiveOn);
    }
}
