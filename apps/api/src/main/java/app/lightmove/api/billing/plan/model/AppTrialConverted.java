package app.lightmove.api.billing.plan.model;

import java.util.UUID;

/** A workspace on the app's own trial was put on a paid plan. Published inside the transaction that did it. */
public record AppTrialConverted(UUID workspaceId) {
}
