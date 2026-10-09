package app.lightmove.api.billing;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingCreditThresholds;
import app.lightmove.api.billing.credit.constant.ContactCreditLevel;
import app.lightmove.api.billing.credit.constant.CreditGrantSource;
import app.lightmove.api.billing.credit.model.ContactCreditThresholdCrossed;
import app.lightmove.api.billing.credit.model.CreditBalanceDrift;
import app.lightmove.api.billing.credit.model.CreditGrantCommand;
import app.lightmove.api.billing.credit.service.CreditBalanceReconcile;
import app.lightmove.api.billing.credit.service.CreditLedgerSweeper;
import app.lightmove.api.billing.credit.service.GrandfatherCredits;
import app.lightmove.api.billing.credit.service.MonthlyCreditReset;
import app.lightmove.api.billing.plan.model.BillingMonth;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The ledger's scheduled side: the month's reset, expiry, the nightly reconcile, and the 80/90/100% thresholds. */
@IntegrationTest
class CreditJobsIntegrationTest extends BillingFlowSupport {

    @Autowired private MonthlyCreditReset reset;
    @Autowired private CreditLedgerSweeper sweeper;
    @Autowired private CreditBalanceReconcile reconcile;
    @Autowired private RecordingCreditThresholds thresholds;
    @Autowired private GrandfatherCredits grandfather;

    @Test
    @DisplayName("a reset expires what is left of last month's plan credits and grants the new month once, run twice")
    void resetExpiresTheOldMonthAndGrantsTheNewOnce() throws Exception {
        UUID workspace = newWorkspace();
        Instant anchor = Instant.now().atZone(ZoneOffset.UTC).minusMonths(1).minusHours(1).toInstant();
        subscribe(workspace, "CORE", 2, null, anchor);
        BillingMonth month = BillingMonth.containing(anchor, Instant.now());
        UUID lastMonth = ledger.grant(new CreditGrantCommand(workspace, CreditGrantSource.PLAN, 100, null,
                Instant.now().plus(Duration.ofDays(1)), BigDecimal.ZERO, MonthlyCreditReset.grantKey(workspace, anchor),
                null, null)).grantId();
        ledger.charge(emailFound(workspace, "last-month-1"));
        ledger.charge(phoneFound(workspace, "last-month-2"));
        db.update("UPDATE app_lm_credit_grant SET effective_at = ?, expires_at = ? WHERE id = ?",
                Timestamp.from(anchor), Timestamp.from(month.start()), lastMonth);

        reset.resetAt(Instant.now());
        reset.resetAt(Instant.now());

        assertThat(remainingOf(lastMonth)).isZero();
        assertThat(db.queryForObject("""
                SELECT available_delta FROM app_lm_credit_entry WHERE grant_id = ? AND kind = 'EXPIRE'""",
                Long.class, lastMonth)).isEqualTo(-94);
        assertThat(db.queryForList("""
                SELECT amount FROM app_lm_credit_grant WHERE workspace_id = ? AND external_ref = ?""",
                Long.class, workspace, MonthlyCreditReset.grantKey(workspace, month.start()))).containsExactly(100L);
        assertThat(ledger.balanceOf(workspace).available()).isEqualTo(100);
        assertLedgerAddsUp(workspace);
    }

    @Test
    @DisplayName("two resets running at once grant the month once")
    void concurrentResetsGrantOnce() throws Exception {
        UUID workspace = newWorkspace();
        subscribe(workspace, "PRO", 1, null, null);

        bothAtOnce(() -> reset.resetAt(Instant.now()));

        assertThat(db.queryForObject("""
                SELECT count(*) FROM app_lm_credit_entry WHERE workspace_id = ? AND kind = 'GRANT'""",
                Long.class, workspace)).isEqualTo(1);
        assertThat(ledger.balanceOf(workspace).available()).isEqualTo(150);
        assertLedgerAddsUp(workspace);
    }

    @Test
    @DisplayName("an Enterprise workspace is granted its agreed pool; a cancelled or seatless one nothing")
    void resetGrantsWhatThePlanSays() throws Exception {
        UUID enterprise = newWorkspace();
        UUID cancelled = newWorkspace();
        UUID seatless = newWorkspace();
        subscribe(enterprise, "ENTERPRISE", 40, 2_000, null);
        subscribe(cancelled, "PRO", 3, null, null);
        db.update("UPDATE app_lm_workspace_subscription SET status = 'CANCELLED' WHERE workspace_id = ?", cancelled);
        subscribe(seatless, "CORE", 0, null, null);

        reset.resetAt(Instant.now());

        assertThat(ledger.balanceOf(enterprise).available()).isEqualTo(2_000);
        assertThat(ledger.balanceOf(cancelled).available()).isZero();
        assertThat(ledger.balanceOf(seatless).available()).isZero();
    }

    @Test
    @DisplayName("expiring bought credits writes an EXPIRE line, and the reconcile finds nothing adrift")
    void expiryLeavesNoDrift() throws Exception {
        UUID workspace = newWorkspace();
        UUID bought = grant(workspace, CreditGrantSource.PURCHASED, 10, Duration.ofDays(365));
        ledger.charge(emailFound(workspace, "before-expiry"));
        db.update("""
                UPDATE app_lm_credit_grant SET effective_at = now() - interval '366 days', expires_at = now() - interval '1 day'
                WHERE id = ?""", bought);

        sweeper.sweepAt(Instant.now());

        assertThat(db.queryForObject("""
                SELECT available_delta FROM app_lm_credit_entry WHERE grant_id = ? AND kind = 'EXPIRE'""",
                Long.class, bought)).isEqualTo(-9);
        assertThat(reconcile.drifts()).extracting(CreditBalanceDrift::workspaceId).doesNotContain(workspace);
        assertLedgerAddsUp(workspace);
    }

    @Test
    @DisplayName("a balance that disagrees with its ledger is reported and audited once, however many instances run")
    void driftIsReportedOnce() throws Exception {
        UUID workspace = newWorkspace();
        grant(workspace, CreditGrantSource.MANUAL, 5, null);
        db.update("UPDATE app_lm_credit_balance SET available = available + 1 WHERE workspace_id = ?", workspace);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        try {
            List<List<CreditBalanceDrift>> runs = bothAtOnce(() -> reconcile.reconcileAt(today));

            assertThat(runs.stream().flatMap(List::stream).filter(drift -> drift.workspaceId().equals(workspace)))
                    .singleElement()
                    .satisfies(drift -> {
                        assertThat(drift.available()).isEqualTo(6);
                        assertThat(drift.ledgerAvailable()).isEqualTo(5);
                        assertThat(drift.grantsRemaining()).isEqualTo(5);
                    });
            assertThat(db.queryForObject("""
                    SELECT count(*) FROM app_lm_audit_event WHERE event_type = 'CREDIT_BALANCE_DRIFT' AND workspace_id = ?""",
                    Integer.class, workspace)).isEqualTo(1);
            assertThat(db.queryForObject("SELECT available FROM app_lm_credit_balance WHERE workspace_id = ?",
                    Long.class, workspace)).as("the reconcile corrects nothing").isEqualTo(6);
        } finally {
            db.update("UPDATE app_lm_credit_balance SET available = available - 1 WHERE workspace_id = ?", workspace);
        }
    }

    @Test
    @DisplayName("spending the month's credits announces 80%, then 90%, then used up, each once")
    void thresholdsAreAnnouncedInTurn() throws Exception {
        UUID workspace = newWorkspace();
        grant(workspace, CreditGrantSource.PLAN, 10, Duration.ofDays(20));
        grant(workspace, CreditGrantSource.PURCHASED, 5, Duration.ofDays(300));

        for (int i = 1; i <= 7; i++) {
            ledger.charge(emailFound(workspace, "email-" + i));
        }
        assertThat(thresholds.of(workspace)).isEmpty();

        ledger.charge(emailFound(workspace, "email-8"));
        ledger.charge(emailFound(workspace, "email-9"));
        ledger.charge(emailFound(workspace, "email-10"));
        assertThat(thresholds.of(workspace)).extracting(ContactCreditThresholdCrossed::level)
                .containsExactly(ContactCreditLevel.EIGHTY, ContactCreditLevel.NINETY);

        ledger.charge(phoneFound(workspace, "phone-1"));
        assertThat(thresholds.of(workspace)).extracting(ContactCreditThresholdCrossed::level)
                .containsExactly(ContactCreditLevel.EIGHTY, ContactCreditLevel.NINETY, ContactCreditLevel.OUT);
        assertThat(thresholds.of(workspace).getLast()).satisfies(out -> {
            assertThat(out.monthlyCredits()).isEqualTo(10);
            assertThat(out.creditsLeft()).isZero();
        });
    }

    @Test
    @DisplayName("concurrent spends crossing 80% announce it exactly once")
    void concurrentSpendsAnnounceOnce() throws Exception {
        UUID workspace = newWorkspace();
        grant(workspace, CreditGrantSource.PLAN, 20, Duration.ofDays(20));
        for (int i = 1; i <= 3; i++) {
            ledger.charge(phoneFound(workspace, "phone-" + i));
        }

        AtomicInteger email = new AtomicInteger();
        bothAtOnce(() -> ledger.charge(emailFound(workspace, "email-" + email.incrementAndGet())));

        assertThat(thresholds.of(workspace)).extracting(ContactCreditThresholdCrossed::level)
                .containsExactly(ContactCreditLevel.EIGHTY);
        assertLedgerAddsUp(workspace);
    }

    @Test
    @DisplayName("a spend that leaps thresholds announces only the highest, and the ones it leapt never follow")
    void leapAnnouncesOnlyTheHighest() throws Exception {
        UUID workspace = newWorkspace();
        grant(workspace, CreditGrantSource.PLAN, 10, Duration.ofDays(20));

        ledger.charge(phoneFound(workspace, "phone-1"));
        ledger.charge(phoneFound(workspace, "phone-2"));
        grant(workspace, CreditGrantSource.MANUAL, 10, null);
        ledger.charge(emailFound(workspace, "email-1"));

        assertThat(thresholds.of(workspace)).extracting(ContactCreditThresholdCrossed::level)
                .containsExactly(ContactCreditLevel.OUT);
    }

    @Test
    @DisplayName("while enforcement is off the grandfather grant waits, its run left unclaimed")
    void grandfatherWaitsForEnforcement() throws Exception {
        UUID workspace = newWorkspace();
        db.update("DELETE FROM app_lm_billing_job_run WHERE job = 'grandfather-credits'");

        assertThat(grandfather.grantAt(Instant.now())).isZero();

        assertThat(ledger.balanceOf(workspace).available()).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM app_lm_billing_job_run WHERE job = 'grandfather-credits'",
                Long.class)).isZero();
    }

    private void subscribe(UUID workspaceId, String plan, int seats, Integer pool, Instant periodStart) {
        Timestamp start = periodStart == null ? null : Timestamp.from(periodStart);
        Timestamp end = periodStart == null ? null
                : Timestamp.from(periodStart.atZone(ZoneOffset.UTC).plusMonths(1).toInstant());
        db.update("""
                INSERT INTO app_lm_workspace_subscription (workspace_id, plan_code, billing_interval, seats,
                                                           contact_credit_pool, status, current_period_start,
                                                           current_period_end)
                VALUES (?, ?, 'MONTHLY', ?, ?, 'INVOICED', ?, ?)""",
                workspaceId, plan, seats, pool, start, end);
    }

    private static <T> List<T> bothAtOnce(Callable<T> work) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Callable<T> gated = () -> {
                start.await();
                return work.call();
            };
            Future<T> first = pool.submit(gated);
            Future<T> second = pool.submit(gated);
            start.countDown();
            return List.of(first.get(), second.get());
        }
    }
}
