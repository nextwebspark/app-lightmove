package app.lightmove.api.billing.credit.service;

import app.lightmove.api.billing.credit.model.CreditBalanceDrift;
import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Checks nightly that every cached balance is what its ledger lines and its grants add up to, and logs and audits a
 * workspace where it is not. It never corrects one: a drift is a bug to find, and the ledger is the record.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreditBalanceReconcile {

    static final String JOB = "credit-balance-reconcile";

    private final BillingJobRuns runs;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final Clock clock;

    @Scheduled(cron = "${lightmove.billing.jobs.reconcile}", zone = "UTC")
    public void scheduledReconcile() {
        reconcileAt(LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC));
    }

    /** @return the drifts reported, or none when another instance already reconciled {@code day} */
    public List<CreditBalanceDrift> reconcileAt(LocalDate day) {
        if (!runs.claim(JOB, day.toString())) {
            return List.of();
        }
        try {
            List<CreditBalanceDrift> drifts = drifts();
            drifts.forEach(this::report);
            return drifts;
        } catch (RuntimeException failure) {
            runs.giveBack(JOB, day.toString());
            throw failure;
        }
    }

    private void report(CreditBalanceDrift drift) {
        log.error("Credit balance drift in workspace {}: cached {} available, {} held; ledger {} available, "
                        + "{} held; grants {} remaining", drift.workspaceId(), drift.available(), drift.held(),
                drift.ledgerAvailable(), drift.ledgerHeld(), drift.grantsRemaining());
        audit.event(WorkspaceEventType.CREDIT_BALANCE_DRIFT).workspace(drift.workspaceId())
                .target("workspace", drift.workspaceId())
                .detail("available", drift.available())
                .detail("held", drift.held())
                .detail("ledgerAvailable", drift.ledgerAvailable())
                .detail("ledgerHeld", drift.ledgerHeld())
                .detail("grantsRemaining", drift.grantsRemaining())
                .record();
    }

    public List<CreditBalanceDrift> drifts() {
        return jdbc.query("""
                SELECT b.workspace_id, b.available, b.held,
                       coalesce(e.available, 0) AS ledger_available, coalesce(e.held, 0) AS ledger_held,
                       coalesce(g.remaining, 0) AS grants_remaining
                FROM app_lm_credit_balance b
                LEFT JOIN (SELECT workspace_id, sum(available_delta) AS available, sum(held_delta) AS held
                           FROM app_lm_credit_entry GROUP BY workspace_id) e ON e.workspace_id = b.workspace_id
                LEFT JOIN (SELECT workspace_id, sum(remaining) AS remaining
                           FROM app_lm_credit_grant GROUP BY workspace_id) g ON g.workspace_id = b.workspace_id
                WHERE b.available <> coalesce(e.available, 0)
                   OR b.held <> coalesce(e.held, 0)
                   OR b.available <> coalesce(g.remaining, 0)
                ORDER BY b.workspace_id""",
                (row, index) -> new CreditBalanceDrift(row.getObject("workspace_id", UUID.class),
                        row.getLong("available"), row.getLong("held"), row.getLong("ledger_available"),
                        row.getLong("ledger_held"), row.getLong("grants_remaining")));
    }
}
