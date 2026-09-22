package app.lightmove.api.project.constant;

import app.lightmove.api.project.model.MandateProgress;

/**
 * Derived, never persisted: nothing in the UI sets health, so a column would be dead schema. The
 * contract stays stable if it is ever persisted later.
 *
 * <p>Health is how far a mandate has fallen behind its own clock — what it has done against how much
 * of the window to its governing milestone it has spent. It used to be a bare date comparison, which
 * meant a mandate nobody had touched for six weeks read as on track until the day it was due.
 */
public enum ProjectHealth {
    OK,
    RISK,
    OFF,
    DONE;

    /**
     * How far behind its clock a mandate may fall before the pill changes. Research arrives in
     * batches — a researcher files ten companies in one sitting — so a mandate sits a few points
     * behind between them, and a tighter threshold would flicker on and off all week.
     */
    private static final double RISK_DEFICIT = 0.15;

    /** Behind by this much, the work left cannot be finished at the pace achieved so far. */
    private static final double OFF_DEFICIT = 0.30;

    /**
     * A mandate's first week is kickoff: the brief is being written and the universe built, so nothing
     * is mapped yet by design. Calling that off track on day three teaches people to ignore the pill,
     * so the first week can reach RISK, which is advisory, but not OFF.
     */
    private static final int SETTLING_IN_DAYS = 7;

    public static ProjectHealth derive(ProjectStage stage, MandateProgress progress) {
        if (stage.isDone()) {
            return DONE;
        }
        if (progress.governingMilestone() == null) {
            return OK;
        }
        // A date that has gone by with the work outstanding is the one fact no smoothing should soften.
        if (progress.milestonePassed()) {
            return progress.completion() >= 1 ? OK : OFF;
        }

        double deficit = progress.elapsedFraction() - progress.completion();
        if (deficit <= RISK_DEFICIT) {
            return OK;
        }
        if (deficit <= OFF_DEFICIT || progress.daysElapsed() < SETTLING_IN_DAYS) {
            return RISK;
        }
        return OFF;
    }
}
