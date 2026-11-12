package app.lightmove.api.core.audit.model;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The customer audit log's filters as its query binds them. Every open filter is a flag beside a placeholder, since
 * Postgres cannot type a bare null bind and an empty {@code IN ()} is a syntax error — {@link #of} is the one place
 * that rule lives.
 */
public record AuditLogQuery(
        UUID workspaceId,
        long before,
        Instant from,
        Instant to,
        boolean anyActor,
        UUID actor,
        boolean anyOutcome,
        String outcome,
        boolean anyType,
        Collection<String> types,
        Collection<String> hiddenTypes,
        boolean withAccount,
        Collection<String> accountTypes
) {

    private static final UUID NOBODY = new UUID(0, 0);
    private static final Instant FAR_FUTURE = Instant.parse("9999-12-31T00:00:00Z");
    private static final List<String> NONE = List.of("-");

    /**
     * @param types the event types shown, or null for every type the workspace's rows carry
     * @param withAccount whether members' account security events are read at all
     */
    public static AuditLogQuery of(UUID workspaceId, Long before, Instant from, Instant to, UUID actor,
                                   String outcome, Collection<String> types, Collection<String> hiddenTypes,
                                   boolean withAccount, Collection<String> accountTypes) {
        return new AuditLogQuery(workspaceId, before == null ? Long.MAX_VALUE : before,
                from == null ? Instant.EPOCH : from, to == null ? FAR_FUTURE : to,
                actor == null, actor == null ? NOBODY : actor,
                outcome == null, outcome == null ? "-" : outcome,
                types == null, types == null || types.isEmpty() ? NONE : types,
                hiddenTypes.isEmpty() ? NONE : hiddenTypes,
                withAccount, accountTypes.isEmpty() ? NONE : accountTypes);
    }
}
