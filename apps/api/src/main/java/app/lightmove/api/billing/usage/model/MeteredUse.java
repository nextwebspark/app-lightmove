package app.lightmove.api.billing.usage.model;

import app.lightmove.api.billing.usage.constant.UsageKind;
import java.util.UUID;

/**
 * One use to record. {@code idempotencyKey} is set where a use must count once per workspace, and null where every
 * press counts.
 */
public record MeteredUse(UUID workspaceId, UUID userId, UUID projectId, UsageKind kind, int units,
                       String idempotencyKey) {

    public static MeteredUse of(UUID workspaceId, UUID userId, UUID projectId, UsageKind kind, int units) {
        return new MeteredUse(workspaceId, userId, projectId, kind, units, null);
    }
}
