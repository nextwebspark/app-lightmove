package app.lightmove.api.billing.plan.repository;

import app.lightmove.api.billing.plan.model.WorkspaceSubscription;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceSubscriptionRepository extends JpaRepository<WorkspaceSubscription, UUID> {

    Optional<WorkspaceSubscription> findByWorkspaceId(UUID workspaceId);
}
