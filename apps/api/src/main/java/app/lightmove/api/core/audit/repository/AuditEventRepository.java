package app.lightmove.api.core.audit.repository;
import app.lightmove.api.core.audit.model.AuditEvent;

import app.lightmove.api.core.audit.constant.AuditOutcome;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Insert-and-read only. There is intentionally no update or delete method — and the database would
 * refuse one anyway.
 */
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    /**
     * One target's own trail, newest first — what the project drawer's activity feed reads. Scoped by
     * workspace as well as by target so a guessed id cannot reach another firm's ledger, and narrowed
     * to what actually happened: a refused attempt belongs to the auditor, not to a team's feed.
     */
    List<AuditEvent> findByWorkspaceIdAndTargetTypeAndTargetIdAndOutcomeOrderByOccurredAtDesc(
            UUID workspaceId, String targetType, String targetId, AuditOutcome outcome, Pageable page);
}
