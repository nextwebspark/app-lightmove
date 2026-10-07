package app.lightmove.api.billing.credit.repository;

import app.lightmove.api.billing.credit.model.CreditHold;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditHoldRepository extends JpaRepository<CreditHold, UUID> {

    Optional<CreditHold> findByWorkspaceIdAndIdempotencyKey(UUID workspaceId, String idempotencyKey);

    Optional<CreditHold> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
