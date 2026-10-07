package app.lightmove.api.billing.credit.repository;

import app.lightmove.api.billing.credit.constant.CreditGrantSource;
import app.lightmove.api.billing.credit.model.CreditGrant;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CreditGrantRepository extends JpaRepository<CreditGrant, UUID> {

    /** Spendable grants in the order a spend drains them: by source, then soonest expiry, then oldest. */
    @Query("""
            SELECT g FROM CreditGrant g
            WHERE g.workspaceId = :workspaceId AND g.remaining > 0 AND g.effectiveAt <= :now
              AND (g.expiresAt IS NULL OR g.expiresAt > :now)
            ORDER BY g.drainRank, g.expiresAt ASC NULLS LAST, g.createdAt, g.id""")
    List<CreditGrant> findSpendable(UUID workspaceId, Instant now);

    @Query("""
            SELECT g FROM CreditGrant g
            WHERE g.workspaceId = :workspaceId AND g.remaining > 0 AND g.expiresAt <= :now""")
    List<CreditGrant> findLapsed(UUID workspaceId, Instant now);

    @Query("""
            SELECT coalesce(sum(g.remaining), 0) FROM CreditGrant g
            WHERE g.workspaceId = :workspaceId AND g.remaining > 0 AND g.expiresAt <= :now""")
    long sumLapsedRemaining(UUID workspaceId, Instant now);

    Optional<CreditGrant> findByWorkspaceIdAndSourceAndExternalRef(UUID workspaceId, CreditGrantSource source,
                                                                   String externalRef);
}
