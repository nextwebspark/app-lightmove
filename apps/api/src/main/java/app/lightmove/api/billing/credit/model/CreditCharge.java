package app.lightmove.api.billing.credit.model;

import app.lightmove.api.billing.credit.constant.CreditAction;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/**
 * One paid action to charge. {@code idempotencyKey} is unique per workspace: a retry with the same key answers the
 * first receipt and spends nothing more.
 */
@NamedInterface(name = "api", propagate = false)
public record CreditCharge(UUID workspaceId, CreditAction action, String idempotencyKey, UUID userId,
                           UUID projectId, UUID personId) {
}
