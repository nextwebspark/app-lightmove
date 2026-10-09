package app.lightmove.api.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.billing.usage.constant.UsageKind;
import app.lightmove.api.billing.usage.model.MeteredUse;
import app.lightmove.api.billing.usage.model.MonthlyUsage;
import app.lightmove.api.billing.usage.service.FairUseGuard;
import app.lightmove.api.billing.usage.service.UsageRecorder;
import app.lightmove.api.billing.usage.service.UsageReport;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/** The fair-use meter: what a use records, the monthly ceiling per seat it is held to, and the margin report. */
@IntegrationTest
@TestPropertySource(properties = {
        "lightmove.billing.fair-use.ai-enrich-per-seat=3",
        "lightmove.billing.fair-use.ai-enrich-cost-fils=6",
        "lightmove.billing.fair-use.assistant-ask-per-seat=0"})
class FairUseIntegrationTest extends BillingFlowSupport {

    @Autowired private FairUseGuard fairUse;
    @Autowired private UsageRecorder usage;
    @Autowired private UsageReport report;

    @Test
    @DisplayName("uses up to the ceiling pass, and the one past it is refused with its kind and when the month resets")
    void theUsePastTheCeilingIsRefused() throws Exception {
        UUID workspaceId = newWorkspace();
        usage.record(MeteredUse.of(workspaceId, null, null, UsageKind.AI_ENRICH, 2));

        fairUse.check(workspaceId, null, UsageKind.AI_ENRICH, 1);
        usage.record(MeteredUse.of(workspaceId, null, null, UsageKind.AI_ENRICH, 1));

        assertThatThrownBy(() -> fairUse.check(workspaceId, null, UsageKind.AI_ENRICH, 1))
                .isInstanceOfSatisfying(ApiException.class, refused -> {
                    assertThat(refused.getCode()).isEqualTo(ErrorCode.FAIR_USE_REACHED);
                    assertThat(refused.getProperties())
                            .containsEntry("kind", "AI_ENRICH")
                            .containsEntry("resetsAt", nextMonth().atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant());
                });
        assertThatThrownBy(() -> fairUse.check(workspaceId, null, UsageKind.AI_ENRICH, 1))
                .isInstanceOf(ApiException.class);
        assertThat(db.queryForObject("""
                SELECT count(*) FROM app_lm_audit_event WHERE event_type = 'FAIR_USE_REACHED' AND workspace_id = ?""",
                Integer.class, workspaceId)).as("a retried refusal is answered, not audited again").isEqualTo(1);
    }

    @Test
    @DisplayName("the ceiling grows with the subscription's staff seats")
    void theCeilingIsPerSeat() throws Exception {
        UUID workspaceId = newWorkspace();
        db.update("""
                INSERT INTO app_lm_workspace_subscription (workspace_id, plan_code, billing_interval, seats, status)
                VALUES (?, 'CORE', 'MONTHLY', 2, 'INVOICED')
                ON CONFLICT (workspace_id) DO UPDATE SET seats = 2""", workspaceId);
        usage.record(MeteredUse.of(workspaceId, null, null, UsageKind.AI_ENRICH, 5));

        fairUse.check(workspaceId, null, UsageKind.AI_ENRICH, 1);

        assertThatThrownBy(() -> fairUse.check(workspaceId, null, UsageKind.AI_ENRICH, 2))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("a ceiling of zero is no ceiling")
    void zeroLiftsTheCeiling() throws Exception {
        UUID workspaceId = newWorkspace();
        usage.record(MeteredUse.of(workspaceId, null, null, UsageKind.ASSISTANT_ASK, 1_000_000));

        fairUse.check(workspaceId, null, UsageKind.ASSISTANT_ASK, 1);
    }

    @Test
    @DisplayName("last month's use does not count against this month's ceiling")
    void lastMonthIsNotCounted() throws Exception {
        UUID workspaceId = newWorkspace();
        recordedIn(workspaceId, UsageKind.AI_ENRICH, 3, lastMonth());

        fairUse.check(workspaceId, null, UsageKind.AI_ENRICH, 3);
    }

    @Test
    @DisplayName("a keyed use is counted once per workspace, at its units times the kind's estimated cost")
    void aKeyedUseIsCountedOnce() throws Exception {
        UUID workspaceId = newWorkspace();
        UUID otherWorkspaceId = newWorkspace();
        MeteredUse page = new MeteredUse(workspaceId, null, null, UsageKind.AI_ENRICH, 2, "page:1");

        usage.record(page);
        usage.record(page);
        usage.record(new MeteredUse(otherWorkspaceId, null, null, UsageKind.AI_ENRICH, 2, "page:1"));

        assertThat(db.queryForList("SELECT est_cost_fils FROM app_lm_usage_event WHERE workspace_id = ?",
                Long.class, workspaceId)).containsExactly(12L);
        assertThat(usage.hasRecorded(workspaceId, "page:1")).isTrue();
        assertThat(usage.hasRecorded(otherWorkspaceId, "page:1")).isTrue();
        assertThat(usage.hasRecorded(workspaceId, "page:2")).isFalse();
    }

    @Test
    @DisplayName("the report sums units and estimated cost per workspace, month and kind")
    void theReportSumsEachMonth() throws Exception {
        UUID workspaceId = newWorkspace();
        recordedIn(workspaceId, UsageKind.AI_ENRICH, 4, lastMonth());
        usage.record(MeteredUse.of(workspaceId, null, null, UsageKind.AI_ENRICH, 1));
        usage.record(MeteredUse.of(workspaceId, null, null, UsageKind.AI_ENRICH, 2));

        assertThat(report.monthly(lastMonth(), YearMonth.now(ZoneOffset.UTC)).stream()
                .filter(row -> row.workspaceId().equals(workspaceId)))
                .containsExactly(
                        new MonthlyUsage(workspaceId, lastMonth(), UsageKind.AI_ENRICH, 4, 0),
                        new MonthlyUsage(workspaceId, YearMonth.now(ZoneOffset.UTC), UsageKind.AI_ENRICH, 3, 18));
    }

    private void recordedIn(UUID workspaceId, UsageKind kind, int units, YearMonth month) {
        Instant at = month.atDay(15).atStartOfDay(ZoneOffset.UTC).toInstant();
        db.update("""
                INSERT INTO app_lm_usage_event (workspace_id, kind, units, est_cost_fils, created_at)
                VALUES (?, ?, ?, 0, ?)""", workspaceId, kind.name(), units, Timestamp.from(at));
    }

    private static YearMonth lastMonth() {
        return YearMonth.now(ZoneOffset.UTC).minusMonths(1);
    }

    private static YearMonth nextMonth() {
        return YearMonth.now(ZoneOffset.UTC).plusMonths(1);
    }
}
