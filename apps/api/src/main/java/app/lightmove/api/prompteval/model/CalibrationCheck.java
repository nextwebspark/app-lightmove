package app.lightmove.api.prompteval.model;

import app.lightmove.api.prompteval.constant.CalibrationCheckStatus;
import java.time.Instant;
import java.util.UUID;

/** One check of the judge against a rubric's labels. {@code agreed} and {@code picks} are null until it finishes. */
public record CalibrationCheck(UUID id, UUID rubricId, int judgeVersion, String judgeModel,
                               CalibrationCheckStatus status, Integer agreed, Integer picks, UUID startedBy,
                               Instant createdAt, Instant finishedAt) {

    public boolean isGoing() {
        return status == CalibrationCheckStatus.QUEUED || status == CalibrationCheckStatus.RUNNING;
    }
}
