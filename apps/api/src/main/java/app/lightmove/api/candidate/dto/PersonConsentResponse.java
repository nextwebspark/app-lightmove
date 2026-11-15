package app.lightmove.api.candidate.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One purpose on the drawer's Privacy block (#1267). {@code purpose}, {@code status} and {@code answeredVia} are
 * {@code ConsentPurpose}, {@code ConsentStatus} and {@code ConsentAnsweredVia} wire tokens; {@code requestOpenUntil}
 * is set while a request (or a renewal of a given consent) waits for an answer; {@code projectId} names the position
 * of a share.
 */
public record PersonConsentResponse(
        UUID id,
        String purpose,
        UUID projectId,
        String status,
        Instant requestedAt,
        String requestedByName,
        Instant requestOpenUntil,
        Instant answeredAt,
        String answeredVia,
        Instant validUntil,
        boolean clientNamed
) {}
