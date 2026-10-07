package app.lightmove.api.billing.credit.model;

import java.util.UUID;

/** A workspace whose cached balance disagrees with the sum of its ledger lines or of its grants' remainders. */
public record CreditBalanceDrift(UUID workspaceId, long available, long held, long ledgerAvailable, long ledgerHeld,
                                 long grantsRemaining) {
}
