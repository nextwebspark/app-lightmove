package app.lightmove.api.billing;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.billing.credit.constant.CreditAction;
import app.lightmove.api.billing.credit.constant.CreditGrantSource;
import app.lightmove.api.billing.credit.model.CreditBalanceSummary;
import app.lightmove.api.billing.credit.model.CreditCharge;
import app.lightmove.api.billing.credit.model.CreditGrantCommand;
import app.lightmove.api.billing.credit.service.CreditLedger;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Workspaces to charge, grants to drain them from, and the check that a ledger adds up to its balance. */
public abstract class BillingFlowSupport extends FlowTestSupport {

    private static final AtomicInteger OWNER = new AtomicInteger();

    @Autowired protected CreditLedger ledger;
    @Autowired protected JdbcTemplate db;

    protected UUID newWorkspace() throws Exception {
        String owner = "owner" + OWNER.incrementAndGet() + "@" + domain;
        return UUID.fromString(createWorkspace(verifiedUser("Yara Haddad", owner), "Credit Firm"));
    }

    protected String superAdmin() throws Exception {
        String address = "platform@" + domain;
        createWorkspace(verifiedUser("Dana Aboud", address), "Platform Firm");
        db.update("""
                INSERT INTO app_lm_user_platform_role (user_id, role_id)
                SELECT u.id, r.id FROM app_lm_user u, app_lm_role r
                WHERE u.email = ? AND r.scope = 'PLATFORM' AND r.name = 'SUPER_ADMIN'
                """, address);
        return login(address);
    }

    protected UUID grant(UUID workspaceId, CreditGrantSource source, long credits, Duration expiresIn) {
        Instant expiresAt = expiresIn == null ? null : Instant.now().plus(expiresIn);
        return ledger.grant(new CreditGrantCommand(workspaceId, source, credits, null, expiresAt, BigDecimal.ZERO,
                null, null, null)).grantId();
    }

    protected static CreditCharge emailFound(UUID workspaceId, String idempotencyKey) {
        return new CreditCharge(workspaceId, CreditAction.EMAIL_FOUND, idempotencyKey, null, null, null);
    }

    protected static CreditCharge phoneFound(UUID workspaceId, String idempotencyKey) {
        return new CreditCharge(workspaceId, CreditAction.PHONE_FOUND, idempotencyKey, null, null, null);
    }

    protected long remainingOf(UUID grantId) {
        return db.queryForObject("SELECT remaining FROM app_lm_credit_grant WHERE id = ?", Long.class, grantId);
    }

    protected long entriesOf(UUID workspaceId) {
        return db.queryForObject("SELECT count(*) FROM app_lm_credit_entry WHERE workspace_id = ?", Long.class,
                workspaceId);
    }

    /** The cached balance is the sum of the ledger, and every grant's remainder is the sum of its own lines. */
    protected void assertLedgerAddsUp(UUID workspaceId) {
        Map<String, Object> sums = db.queryForMap("""
                SELECT coalesce(sum(available_delta), 0) AS available, coalesce(sum(held_delta), 0) AS held
                FROM app_lm_credit_entry WHERE workspace_id = ?""", workspaceId);
        CreditBalanceSummary balance = ledger.balanceOf(workspaceId);
        assertThat(((Number) sums.get("available")).longValue()).isEqualTo(balance.available());
        assertThat(((Number) sums.get("held")).longValue()).isEqualTo(balance.held());
        assertThat(db.queryForObject("""
                SELECT count(*) FROM app_lm_credit_grant g
                WHERE g.workspace_id = ?
                  AND g.remaining <> (SELECT coalesce(sum(e.available_delta), 0)
                                      FROM app_lm_credit_entry e WHERE e.grant_id = g.id)""",
                Long.class, workspaceId)).isZero();
    }
}
