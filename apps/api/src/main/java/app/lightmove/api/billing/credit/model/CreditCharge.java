package app.lightmove.api.billing.credit.model;

import app.lightmove.api.billing.credit.constant.CreditAction;
import java.util.UUID;

/**
 * One paid action to charge. {@code idempotencyKey} is unique per workspace: a retry with the same key answers the
 * first receipt and spends nothing more.
 */
public record CreditCharge(UUID workspaceId, CreditAction action, String idempotencyKey, UUID userId,
                           UUID projectId, UUID personId) {
}
