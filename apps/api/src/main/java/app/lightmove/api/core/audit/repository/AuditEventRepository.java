package app.lightmove.api.core.audit.repository;
import app.lightmove.api.core.audit.model.AuditEvent;
import app.lightmove.api.core.audit.model.AuditLogQuery;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Insert-and-read only. There is intentionally no update or delete method — and the database would
 * refuse one anyway.
 */
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    /**
     * One target's successful events, newest first, older than {@code beforeId} — a cursor on the
     * identity rather than an offset, so a page loaded later does not repeat rows written meanwhile.
     *
     * <p>{@code eventTypes} are kept whatever they carry; {@code statusEventTypes} only when their
     * metadata names a {@code status}, which is how a stage or status move is told apart from the
     * note edits and profile saves recorded under the same type.
     *
     * <p>The workspace is part of the filter even though the caller has already proved the target is
     * its own: a target id is only unique inside its type, never across tenants by construction.
     */
    @Query(
            value = "select * from app_lm_audit_event e "
                    + "where e.workspace_id = :workspaceId "
                    + "and e.target_type = :targetType and e.target_id = :targetId "
                    + "and e.outcome = 'SUCCESS' and e.id < :beforeId "
                    + "and (e.event_type in (:eventTypes) "
                    + "  or (e.event_type in (:statusEventTypes) and e.metadata ->> 'status' is not null)) "
                    + "order by e.id desc limit :limit",
            nativeQuery = true)
    List<AuditEvent> findLatestForTarget(UUID workspaceId, String targetType, String targetId,
                                         Collection<String> eventTypes, Collection<String> statusEventTypes,
                                         long beforeId, int limit);

    /**
     * The customer audit log, newest first by id: the workspace's own rows, and its members' account security events
     * (written with no workspace) from each one's latest joining until the removal that ended it. Two branches, each
     * on its own index; an {@code OR} would walk the whole trail backwards.
     */
    @Query(value = WORKSPACE_LOG_SELECT + " order by u.id desc limit :limit", nativeQuery = true)
    List<AuditEvent> findWorkspaceLog(AuditLogQuery query, int limit);

    /** {@link #findWorkspaceLog}'s rows counted, at most {@code limit} per branch: enough to tell a ceiling is passed. */
    @Query(value = "select count(*) from (" + WORKSPACE_LOG_SELECT + ") counted", nativeQuery = true)
    long countWorkspaceLog(AuditLogQuery query, int limit);

    String WORKSPACE_LOG_FILTERS = """
            e.id < :#{#query.before} and e.occurred_at >= :#{#query.from} and e.occurred_at < :#{#query.to}
              and (:#{#query.anyActor} = true or e.actor_user_id = :#{#query.actor})
              and (:#{#query.anyOutcome} = true or e.outcome = :#{#query.outcome})
              and (:#{#query.anyType} = true or e.event_type in (:#{#query.types}))
            """;

    String WORKSPACE_LOG_SELECT = """
            select u.* from (
              (select e.* from app_lm_audit_event e
                where e.workspace_id = :#{#query.workspaceId} and e.event_type not in (:#{#query.hiddenTypes})
                  and\s""" + WORKSPACE_LOG_FILTERS + """
                order by e.id desc limit :limit)
              union all
              (select e.* from app_lm_audit_event e
                where :#{#query.withAccount} = true and e.workspace_id is null
                  and e.event_type in (:#{#query.accountTypes})
                  and\s""" + WORKSPACE_LOG_FILTERS + """
                  and exists (select 1 from app_lm_workspace_member m
                      where m.workspace_id = :#{#query.workspaceId} and m.user_id = e.actor_user_id
                        and e.occurred_at >= coalesce(m.joined_at, m.created_at)
                        and (m.status = 'ACTIVE' or e.occurred_at < (
                            select min(r.occurred_at) from app_lm_audit_event r
                            where r.target_type = 'member' and r.target_id = cast(m.id as text) and r.outcome = 'SUCCESS'
                              and r.workspace_id = :#{#query.workspaceId} and r.event_type = 'MEMBER_REMOVED'
                              and r.occurred_at >= coalesce(m.joined_at, m.created_at))))
                order by e.id desc limit :limit)
            ) u
            """;
}
