package app.lightmove.api.billing.credit.model;

import app.lightmove.api.billing.credit.constant.ContactCreditLevel;
import java.time.Instant;
import java.util.UUID;

/**
 * A spend took a workspace's contact credits past 80%, 90% or to none left, once per billing month and threshold.
 * Published inside the ledger's transaction: a listener that acts on it listens after commit.
 */
public record ContactCreditThresholdCrossed(UUID workspaceId, ContactCreditLevel level, Instant monthStart,
                                            Instant resetsAt, long monthlyCredits, long creditsLeft) {
}
