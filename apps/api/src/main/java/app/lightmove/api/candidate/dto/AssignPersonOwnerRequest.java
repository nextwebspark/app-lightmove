package app.lightmove.api.candidate.dto;

import java.util.UUID;

/** Who keeps the relationship: a staff member of the workspace, or null for nobody. */
public record AssignPersonOwnerRequest(UUID ownerUserId) {}
