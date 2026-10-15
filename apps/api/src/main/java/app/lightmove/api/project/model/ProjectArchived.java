package app.lightmove.api.project.model;

import java.util.UUID;

/** A position was archived, published in the transaction that archives it; listeners stop its work in that one. */
public record ProjectArchived(UUID workspaceId, UUID projectId, UUID actorUserId) {}
