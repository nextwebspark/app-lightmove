package app.lightmove.api.project.model;

import java.util.UUID;

/** A position was created, published in the transaction that creates it; listeners set up its parts in that one. */
public record ProjectCreated(UUID workspaceId, UUID projectId, String positionTitle, String hqCountry,
                             UUID actorUserId) {}
