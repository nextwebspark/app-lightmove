package app.lightmove.api.billing.credit.repository;

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

    @Query("""
            SELECT h FROM CreditHold h
            WHERE h.workspaceId = :workspaceId AND h.status = app.lightmove.api.billing.credit.constant.CreditHoldStatus.OPEN
              AND h.expiresAt <= :now
            ORDER BY h.expiresAt""")
    List<CreditHold> findStale(UUID workspaceId, Instant now);

    /** Workspaces with an unsettled hold past its time or a grant past its expiry still holding credits. */
    @Query(nativeQuery = true, value = """
            SELECT workspace_id FROM app_lm_credit_hold WHERE status = 'OPEN' AND expires_at <= :now
            UNION
            SELECT workspace_id FROM app_lm_credit_grant WHERE remaining > 0 AND expires_at <= :now
            LIMIT :limit""")
    List<UUID> findWorkspacesWithLapsedCredits(Instant now, int limit);
}
