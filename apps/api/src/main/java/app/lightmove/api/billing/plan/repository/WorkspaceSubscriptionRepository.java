package app.lightmove.api.billing.plan.repository;

import app.lightmove.api.billing.plan.model.WorkspaceSubscription;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WorkspaceSubscriptionRepository extends JpaRepository<WorkspaceSubscription, UUID> {

    Optional<WorkspaceSubscription> findByWorkspaceId(UUID workspaceId);

    /** Read by SQL rather than through {@code workspace}, which billing never depends on. */
    @Query(nativeQuery = true, value = "SELECT EXISTS (SELECT 1 FROM app_lm_workspace WHERE id = :workspaceId)")
    boolean workspaceExists(UUID workspaceId);
}
