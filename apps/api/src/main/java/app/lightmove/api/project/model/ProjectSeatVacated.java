package app.lightmove.api.project.model;

import java.util.UUID;

/**
 * A staff member left a position's team, published in the transaction that removes the seat — by a lead, or
 * because they left the workspace ({@code actorUserId} null). Listeners run in it, before the seat is gone.
 */
public record ProjectSeatVacated(UUID workspaceId, UUID projectId, UUID userId, UUID actorUserId) {}
