package app.lightmove.api.billing.credit.dto;

import app.lightmove.api.billing.credit.constant.CreditGrantSource;
import java.time.Instant;
import java.util.UUID;

/** The grant, whether this request wrote it, and the workspace's balance after it. */
public record CreditGrantResponse(UUID grantId, CreditGrantSource source, long credits, Instant expiresAt,
                                  boolean alreadyGranted, long available, long held) {
}
