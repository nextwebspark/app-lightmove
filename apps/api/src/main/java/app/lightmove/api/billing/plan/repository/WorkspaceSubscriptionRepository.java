package app.lightmove.api.billing.plan.repository;

import app.lightmove.api.billing.plan.model.WorkspaceSubscription;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface WorkspaceSubscriptionRepository extends JpaRepository<WorkspaceSubscription, UUID> {

    Optional<WorkspaceSubscription> findByWorkspaceId(UUID workspaceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM WorkspaceSubscription s WHERE s.workspaceId = :workspaceId")
    Optional<WorkspaceSubscription> findForUpdate(UUID workspaceId);

    /** 1 where Stripe bills the workspace and its quantity now owes a sync, 0 where nothing is billed per seat online. */
    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE app_lm_workspace_subscription SET seat_sync_due_at = :now
            WHERE workspace_id = :workspaceId AND stripe_subscription_id IS NOT NULL AND status <> 'CANCELLED'""")
    int markSeatSyncDue(UUID workspaceId, Instant now);

    /** Clears only the request a sync read: one made while it ran stays for the next. */
    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE app_lm_workspace_subscription SET seat_sync_due_at = NULL
            WHERE workspace_id = :workspaceId AND seat_sync_due_at = :dueAt""")
    int clearSeatSync(UUID workspaceId, Instant dueAt);

    @Query(nativeQuery = true, value = """
            SELECT workspace_id FROM app_lm_workspace_subscription
            WHERE seat_sync_due_at <= :before ORDER BY seat_sync_due_at LIMIT :limit""")
    List<UUID> findWorkspacesOwingSeatSync(Instant before, int limit);
}
