package app.lightmove.api.core.audit.repository;
import app.lightmove.api.core.audit.model.AuditEvent;

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
}
