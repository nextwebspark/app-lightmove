package app.lightmove.api.billing.credit.repository;

import app.lightmove.api.billing.credit.constant.CreditGrantSource;
import app.lightmove.api.billing.credit.model.CreditGrant;
import app.lightmove.api.billing.credit.model.MonthlyCredits;
import app.lightmove.api.billing.credit.model.SourceCredits;
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
            SELECT new app.lightmove.api.billing.credit.model.SourceCredits(g.source, sum(g.remaining))
            FROM CreditGrant g
            WHERE g.workspaceId = :workspaceId AND g.remaining > 0 AND g.effectiveAt <= :now
              AND (g.expiresAt IS NULL OR g.expiresAt > :now)
            GROUP BY g.source""")
    List<SourceCredits> spendableBySource(UUID workspaceId, Instant now);

    @Query("""
            SELECT g FROM CreditGrant g
            WHERE g.workspaceId = :workspaceId AND g.remaining > 0 AND g.expiresAt <= :now""")
    List<CreditGrant> findLapsed(UUID workspaceId, Instant now);

    @Query("""
            SELECT coalesce(sum(g.remaining), 0) FROM CreditGrant g
            WHERE g.workspaceId = :workspaceId AND g.remaining > 0 AND g.expiresAt <= :now""")
    long sumLapsedRemaining(UUID workspaceId, Instant now);

    /** Bought credits still unspent that lapse after {@code now} and by {@code horizon}, soonest first. */
    @Query("""
            SELECT g FROM CreditGrant g
            WHERE g.source = app.lightmove.api.billing.credit.constant.CreditGrantSource.PURCHASED
              AND g.remaining > 0 AND g.expiresAt > :now AND g.expiresAt <= :horizon
            ORDER BY g.expiresAt, g.id""")
    List<CreditGrant> findPurchasedExpiring(Instant now, Instant horizon);

    /** The plan credits in force now: the month's grant, and any a seat added during it. */
    @Query("""
            SELECT new app.lightmove.api.billing.credit.model.MonthlyCredits(
                coalesce(sum(g.amount), 0), coalesce(sum(g.remaining), 0))
            FROM CreditGrant g
            WHERE g.workspaceId = :workspaceId
              AND g.source = app.lightmove.api.billing.credit.constant.CreditGrantSource.PLAN
              AND g.effectiveAt <= :now AND g.expiresAt > :now""")
    MonthlyCredits monthlyCreditsOf(UUID workspaceId, Instant now);

    /**
     * Workspaces past {@code after} on a live plan with credits to grant and no month's grant in force now. A
     * subscription past due since before {@code graceStart} is not live until Stripe is paid.
     */
    @Query(nativeQuery = true, value = """
            SELECT s.workspace_id
            FROM app_lm_workspace_subscription s
            JOIN app_lm_billing_plan p ON p.code = s.plan_code
            WHERE s.workspace_id > :after
              AND (s.status IN ('ACTIVE', 'TRIALING', 'INVOICED')
                   OR (s.status = 'PAST_DUE' AND s.past_due_since > :graceStart))
              AND CASE WHEN p.custom THEN coalesce(s.contact_credit_pool, 0)
                       ELSE s.seats * p.contact_credits_per_seat END > 0
              AND NOT EXISTS (SELECT 1 FROM app_lm_credit_grant g
                              WHERE g.workspace_id = s.workspace_id AND g.source = 'PLAN'
                                AND g.external_ref LIKE 'plan:%'
                                AND g.effective_at <= :now AND g.expires_at > :now)
            ORDER BY s.workspace_id
            LIMIT :limit""")
    List<UUID> findWorkspacesDueMonthlyCredits(Instant now, Instant graceStart, UUID after, int limit);

    Optional<CreditGrant> findByWorkspaceIdAndSourceAndExternalRef(UUID workspaceId, CreditGrantSource source,
                                                                   String externalRef);
}
