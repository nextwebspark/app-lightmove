package app.lightmove.api.project.constant;

import java.time.LocalDate;

/**
 * Derived, never persisted: nothing in the UI sets health, so a column would be dead schema. The
 * contract stays stable if it is ever persisted later.
 */
public enum ProjectHealth {
    OK,
    RISK,
    OFF,
    DONE;

    private static final int RISK_WINDOW_DAYS = 30;

    public static ProjectHealth derive(ProjectStage stage, LocalDate targetDate, LocalDate today) {
        if (stage.isDone()) {
            return DONE;
        }
        if (targetDate == null) {
            return OK;
        }
        if (targetDate.isBefore(today)) {
            return OFF;
        }
        boolean closeToTarget = !targetDate.isAfter(today.plusDays(RISK_WINDOW_DAYS));
        boolean beforeOutreach = stage.ordinal() < ProjectStage.OUTREACH.ordinal();
        return closeToTarget && beforeOutreach ? RISK : OK;
    }
}
