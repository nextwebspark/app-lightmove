package app.lightmove.api.prompteval.model;

import java.util.UUID;

/** A check this instance is working, and the token its writes prove that with. */
public record ClaimedCalibrationCheck(CalibrationCheck check, UUID claimToken) {}
