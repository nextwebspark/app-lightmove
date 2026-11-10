package app.lightmove.api.account.model;

import java.util.List;
import java.util.UUID;

/**
 * An account deleted, announced in its transaction; {@code workspaceIds} is every workspace it ever held a row in. The
 * former address and name are carried for the confirmation sent once it commits, since the row no longer holds them.
 */
public record AccountDeleted(UUID userId, List<UUID> workspaceIds, String formerEmail, String formerName) {

    @Override
    public String toString() {
        return "AccountDeleted[userId=" + userId + ", workspaces=" + workspaceIds.size() + "]";
    }
}
