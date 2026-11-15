package app.lightmove.api.candidate.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One person's share-with-client consent on a position (#1267 phase 3), keyed by their candidate row. {@code status}
 * is a {@code ConsentStatus} wire token, null for someone never asked; {@code requestOpenUntil} is set only while a
 * request still waits for an answer, null once it lapsed even before the expiry scan closes it.
 */
public record ShareConsentRowResponse(
        UUID candidateId,
        String status,
        Instant requestedAt,
        Instant requestOpenUntil,
        Instant answeredAt,
        boolean clientNamed
) {}
