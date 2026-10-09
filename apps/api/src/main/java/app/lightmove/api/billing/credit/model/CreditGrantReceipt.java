package app.lightmove.api.billing.credit.model;

import app.lightmove.api.billing.credit.constant.CreditGrantSource;
import java.time.Instant;
import java.util.UUID;

/** A grant as written, or as found when its external reference had already been granted. */
public record CreditGrantReceipt(UUID grantId, CreditGrantSource source, long credits, Instant expiresAt,
                                 boolean alreadyGranted) {
}
