package app.lightmove.api.billing.credit.model;

import app.lightmove.api.billing.credit.constant.CreditGrantSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Credits to add to a workspace; a second grant with the same source and {@code externalRef} adds nothing. */
public record CreditGrantCommand(UUID workspaceId, CreditGrantSource source, long credits, Instant effectiveAt,
                                 Instant expiresAt, BigDecimal filsPerCredit, String externalRef, UUID grantedBy,
                                 String note) {
}
