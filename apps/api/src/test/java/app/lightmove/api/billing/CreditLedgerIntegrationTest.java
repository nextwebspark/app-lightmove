package app.lightmove.api.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.billing.credit.constant.CreditGrantSource;
import app.lightmove.api.billing.credit.constant.CreditHoldStatus;
import app.lightmove.api.billing.credit.model.CreditGrantCommand;
import app.lightmove.api.billing.credit.model.CreditGrantReceipt;
import app.lightmove.api.billing.credit.model.CreditReceipt;
import app.lightmove.api.billing.credit.service.CreditLedgerSweeper;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

/** The contact-credit ledger as it runs today, recording rather than refusing. */
@IntegrationTest
class CreditLedgerIntegrationTest extends BillingFlowSupport {

    @Autowired CreditLedgerSweeper sweeper;

    @Test
    @DisplayName("a spend drains the month's credits, then given ones, then bought ones, soonest expiry first")
    void drainsPlanThenGivenThenPurchased() throws Exception {
        UUID workspace = newWorkspace();
        UUID purchased = grant(workspace, CreditGrantSource.PURCHASED, 5, Duration.ofDays(300));
        UUID promo = grant(workspace, CreditGrantSource.PROMO, 2, Duration.ofDays(30));
        UUID manual = grant(workspace, CreditGrantSource.MANUAL, 2, null);
        UUID laterPlan = grant(workspace, CreditGrantSource.PLAN, 3, Duration.ofDays(20));
        UUID soonerPlan = grant(workspace, CreditGrantSource.PLAN, 1, Duration.ofDays(10));

        ledger.charge(phoneFound(workspace, "phone-1"));
        assertThat(remainingOf(soonerPlan)).isZero();
        assertThat(remainingOf(laterPlan)).isZero();
        assertThat(remainingOf(promo)).isEqualTo(1);
        assertThat(remainingOf(manual)).isEqualTo(2);
        assertThat(remainingOf(purchased)).isEqualTo(5);

        ledger.charge(phoneFound(workspace, "phone-2"));
        assertThat(remainingOf(promo)).isZero();
        assertThat(remainingOf(manual)).isZero();
        assertThat(remainingOf(purchased)).isEqualTo(3);
        assertThat(ledger.balanceOf(workspace).available()).isEqualTo(3);
        assertLedgerAddsUp(workspace);
    }

    @Test
    @DisplayName("a retried idempotency key answers the first receipt and writes nothing")
    void retriedKeyWritesNothing() throws Exception {
        UUID workspace = newWorkspace();
        grant(workspace, CreditGrantSource.MANUAL, 10, null);

        CreditReceipt first = ledger.charge(emailFound(workspace, "lookup-42"));
        long written = entriesOf(workspace);
        CreditReceipt retried = ledger.charge(emailFound(workspace, "lookup-42"));

        assertThat(retried).isEqualTo(first);
        assertThat(entriesOf(workspace)).isEqualTo(written);
        assertThat(ledger.balanceOf(workspace).available()).isEqualTo(9);
        assertLedgerAddsUp(workspace);
    }

    @Test
    @DisplayName("a hold reserves credits, and releasing it gives every one back")
    void holdThenReleaseRestoresTheBalance() throws Exception {
        UUID workspace = newWorkspace();
        UUID manual = grant(workspace, CreditGrantSource.MANUAL, 7, null);

        CreditReceipt hold = ledger.hold(phoneFound(workspace, "phone-held"));
        assertThat(ledger.balanceOf(workspace).available()).isEqualTo(2);
        assertThat(ledger.balanceOf(workspace).held()).isEqualTo(5);
        assertLedgerAddsUp(workspace);

        CreditReceipt released = ledger.release(workspace, hold.holdId());
        assertThat(released.status()).isEqualTo(CreditHoldStatus.RELEASED);
        assertThat(ledger.balanceOf(workspace).available()).isEqualTo(7);
        assertThat(ledger.balanceOf(workspace).held()).isZero();
        assertThat(remainingOf(manual)).isEqualTo(7);
        assertLedgerAddsUp(workspace);
    }

    @Test
    @DisplayName("a captured hold leaves the held credits spent")
    void captureSpendsTheHeldCredits() throws Exception {
        UUID workspace = newWorkspace();
        grant(workspace, CreditGrantSource.MANUAL, 7, null);

        CreditReceipt hold = ledger.hold(emailFound(workspace, "email-held"));
        CreditReceipt captured = ledger.capture(workspace, hold.holdId());

        assertThat(captured.status()).isEqualTo(CreditHoldStatus.CAPTURED);
        assertThat(ledger.balanceOf(workspace).available()).isEqualTo(6);
        assertThat(ledger.balanceOf(workspace).held()).isZero();
        assertThat(ledger.capture(workspace, hold.holdId())).isEqualTo(captured);
        assertLedgerAddsUp(workspace);
    }

    @Test
    @DisplayName("a refund returns a spend's credits to the grants it drained")
    void refundReturnsCreditsToTheirGrants() throws Exception {
        UUID workspace = newWorkspace();
        UUID manual = grant(workspace, CreditGrantSource.MANUAL, 6, null);

        CreditReceipt spent = ledger.charge(phoneFound(workspace, "phone-wrong-number"));
        ledger.refund(workspace, spent.holdId());

        assertThat(remainingOf(manual)).isEqualTo(6);
        assertThat(ledger.balanceOf(workspace).available()).isEqualTo(6);
        assertLedgerAddsUp(workspace);
    }

    @Test
    @DisplayName("one idempotency key used in two workspaces charges each of them")
    void keysDoNotCollideAcrossWorkspaces() throws Exception {
        UUID first = newWorkspace();
        UUID second = newWorkspace();
        grant(first, CreditGrantSource.MANUAL, 3, null);
        grant(second, CreditGrantSource.MANUAL, 3, null);

        CreditReceipt inFirst = ledger.charge(emailFound(first, "shared-key"));
        CreditReceipt inSecond = ledger.charge(emailFound(second, "shared-key"));

        assertThat(inSecond.holdId()).isNotEqualTo(inFirst.holdId());
        assertThat(ledger.balanceOf(first).available()).isEqualTo(2);
        assertThat(ledger.balanceOf(second).available()).isEqualTo(2);
    }

    @Test
    @DisplayName("while enforcement is off, a spend the credits cannot cover is recorded as an overdraft")
    void shortfallIsRecordedAsAnOverdraft() throws Exception {
        UUID workspace = newWorkspace();
        grant(workspace, CreditGrantSource.MANUAL, 2, null);

        CreditReceipt spent = ledger.charge(phoneFound(workspace, "phone-short"));

        assertThat(spent.credits()).isEqualTo(5);
        assertThat(spent.covered()).isEqualTo(2);
        assertThat(ledger.balanceOf(workspace).available()).isZero();
        assertThat(db.queryForObject("""
                SELECT overdraft FROM app_lm_credit_entry WHERE workspace_id = ? AND kind = 'ADJUST'""",
                Long.class, workspace)).isEqualTo(3);
        assertLedgerAddsUp(workspace);
    }

    @Test
    @DisplayName("a grant whose external reference was already granted adds nothing")
    void externalReferenceIsGrantedOnce() throws Exception {
        UUID workspace = newWorkspace();
        CreditGrantCommand invoice = new CreditGrantCommand(workspace, CreditGrantSource.PLAN, 150, null, null,
                new BigDecimal("33.266667"), "in_1Q2w3E", null, null);

        CreditGrantReceipt first = ledger.grant(invoice);
        CreditGrantReceipt again = ledger.grant(invoice);

        assertThat(again.alreadyGranted()).isTrue();
        assertThat(again.grantId()).isEqualTo(first.grantId());
        assertThat(ledger.balanceOf(workspace).available()).isEqualTo(150);
        assertLedgerAddsUp(workspace);
    }

    @Test
    @DisplayName("a lapsed grant leaves the balance at once and is expired by the next write")
    void lapsedGrantIsExpired() throws Exception {
        UUID workspace = newWorkspace();
        UUID lapsing = grant(workspace, CreditGrantSource.PLAN, 4, Duration.ofDays(1));
        grant(workspace, CreditGrantSource.MANUAL, 3, null);
        db.update("""
                UPDATE app_lm_credit_grant SET effective_at = now() - interval '2 days', expires_at = now() - interval '1 day'
                WHERE id = ?""", lapsing);

        assertThat(ledger.balanceOf(workspace).available()).isEqualTo(3);

        ledger.charge(emailFound(workspace, "after-lapse"));
        assertThat(remainingOf(lapsing)).isZero();
        assertThat(ledger.balanceOf(workspace).available()).isEqualTo(2);
        assertThat(db.queryForObject("""
                SELECT available_delta FROM app_lm_credit_entry WHERE grant_id = ? AND kind = 'EXPIRE'""",
                Long.class, lapsing)).isEqualTo(-4);
        assertLedgerAddsUp(workspace);
    }

    @Test
    @DisplayName("the sweeper releases a hold nobody settled in time, and expires what lapsed")
    void sweeperReleasesStaleHolds() throws Exception {
        UUID workspace = newWorkspace();
        UUID manual = grant(workspace, CreditGrantSource.MANUAL, 6, null);
        CreditReceipt abandoned = ledger.hold(phoneFound(workspace, "worker-died"));
        db.update("UPDATE app_lm_credit_hold SET expires_at = now() - interval '1 minute' WHERE id = ?",
                abandoned.holdId());

        sweeper.sweepAt(Instant.now());

        assertThat(db.queryForObject("SELECT status FROM app_lm_credit_hold WHERE id = ?", String.class,
                abandoned.holdId())).isEqualTo("RELEASED");
        assertThat(remainingOf(manual)).isEqualTo(6);
        assertThat(ledger.balanceOf(workspace).held()).isZero();
        assertLedgerAddsUp(workspace);
    }

    @Test
    @DisplayName("a key replayed for another action, or a charge replaying an uncaptured hold, is refused")
    void reusedKeyForAnotherChargeIsRefused() throws Exception {
        UUID workspace = newWorkspace();
        grant(workspace, CreditGrantSource.MANUAL, 10, null);
        ledger.hold(emailFound(workspace, "lookup-7"));

        assertThatThrownBy(() -> ledger.hold(phoneFound(workspace, "lookup-7")))
                .isInstanceOfSatisfying(ApiException.class, refused ->
                        assertThat(refused.getCode()).isEqualTo(ErrorCode.CREDIT_IDEMPOTENCY_KEY_REUSED));
        assertThatThrownBy(() -> ledger.charge(emailFound(workspace, "lookup-7")))
                .isInstanceOfSatisfying(ApiException.class, refused ->
                        assertThat(refused.getCode()).isEqualTo(ErrorCode.CREDIT_IDEMPOTENCY_KEY_REUSED));
        assertLedgerAddsUp(workspace);
    }

    @Test
    @DisplayName("a released hold cannot be captured, and another workspace's hold is not found")
    void settledAndForeignHoldsAreRefused() throws Exception {
        UUID workspace = newWorkspace();
        UUID other = newWorkspace();
        grant(workspace, CreditGrantSource.MANUAL, 5, null);
        CreditReceipt hold = ledger.hold(emailFound(workspace, "released-then-captured"));
        ledger.release(workspace, hold.holdId());

        assertThatThrownBy(() -> ledger.capture(workspace, hold.holdId()))
                .isInstanceOfSatisfying(ApiException.class, refused ->
                        assertThat(refused.getCode()).isEqualTo(ErrorCode.CREDIT_HOLD_SETTLED));
        assertThatThrownBy(() -> ledger.release(other, hold.holdId()))
                .isInstanceOfSatisfying(ApiException.class, refused ->
                        assertThat(refused.getCode()).isEqualTo(ErrorCode.CREDIT_HOLD_NOT_FOUND));
    }

    @Test
    @DisplayName("a ledger line can be neither changed nor removed")
    void entriesAreAppendOnly() throws Exception {
        UUID workspace = newWorkspace();
        grant(workspace, CreditGrantSource.MANUAL, 1, null);

        assertThatThrownBy(() -> db.update(
                "UPDATE app_lm_credit_entry SET available_delta = 1000 WHERE workspace_id = ?", workspace))
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("append-only");
        assertThatThrownBy(() -> db.update("DELETE FROM app_lm_credit_entry WHERE workspace_id = ?", workspace))
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("append-only");
    }
}
