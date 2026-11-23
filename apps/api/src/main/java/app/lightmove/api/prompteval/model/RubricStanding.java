package app.lightmove.api.prompteval.model;

import app.lightmove.api.prompteval.constant.RubricState;

/**
 * Where a rubric stands now. {@code agreed} of {@code picks} are the last finished check's, null when there is
 * none; {@code check} is the check going, else the last one, else null.
 */
public record RubricStanding(RubricState state, Integer agreed, Integer picks, CalibrationCheck check) {

    public Double agreementPercent() {
        return agreed == null || picks == null || picks == 0 ? null : Math.round(1000.0 * agreed / picks) / 10.0;
    }

    public boolean counts() {
        return state == RubricState.COUNTS;
    }
}
