package app.lightmove.api.assistant.tool;

import java.util.UUID;

/**
 * Who a tool call is authorised as.
 *
 * <p>Rebuilt on the worker thread from the turn row rather than taken from a {@code SecurityContext},
 * which a background thread does not carry. V65 stores {@code actor_user_id} and {@code workspace_id}
 * for exactly this, and says what it is and is not: naming the actor is not a shortcut past
 * authorisation, because the guard beans are still called and still re-read the database on every
 * call. It only says whose membership to read.
 *
 * <p>Deliberately not an {@code AuthPrincipal}. That type also carries roles minted up to fifteen
 * minutes ago, and nothing may branch on them; carrying only the two identifiers the rbac services
 * actually read removes the temptation.
 *
 * @param turnId the turn these calls belong to, carried so a denial is auditable against it
 */
public record AssistantToolCaller(UUID userId, UUID workspaceId, UUID turnId) {

    public AssistantToolCaller {
        if (userId == null || workspaceId == null || turnId == null) {
            throw new IllegalArgumentException("a tool caller needs a user, a workspace and a turn");
        }
    }
}
