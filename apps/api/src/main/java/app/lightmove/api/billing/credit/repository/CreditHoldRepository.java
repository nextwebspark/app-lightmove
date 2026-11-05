package app.lightmove.api.billing.credit.repository;

import app.lightmove.api.billing.credit.constant.CreditAction;
import app.lightmove.api.billing.credit.constant.CreditHoldStatus;
import app.lightmove.api.billing.credit.model.CreditHold;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CreditHoldRepository extends JpaRepository<CreditHold, UUID> {

    Optional<CreditHold> findByWorkspaceIdAndIdempotencyKey(UUID workspaceId, String idempotencyKey);

    Optional<CreditHold> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    long countByWorkspaceIdAndPersonIdAndActionAndStatus(UUID workspaceId, UUID personId, CreditAction action,
                                                         CreditHoldStatus status);

    @Query("""
            SELECT h FROM CreditHold h
            WHERE h.workspaceId = :workspaceId AND h.status = app.lightmove.api.billing.credit.constant.CreditHoldStatus.OPEN
              AND h.expiresAt <= :now
            ORDER BY h.expiresAt""")
    List<CreditHold> findStale(UUID workspaceId, Instant now);

}
