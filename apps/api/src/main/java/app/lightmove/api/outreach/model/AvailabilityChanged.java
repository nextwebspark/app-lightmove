package app.lightmove.api.outreach.model;

import java.util.UUID;

/** A workspace's working hours changed ({@code userId} null), or one consultant's; their booking pages follow. */
public record AvailabilityChanged(UUID workspaceId, UUID userId) {}
