package app.lightmove.api.account.model;

import java.util.List;
import java.util.UUID;

/** An account deleted, announced in its transaction; {@code workspaceIds} is every workspace it ever held a row in. */
public record AccountDeleted(UUID userId, List<UUID> workspaceIds) {}
