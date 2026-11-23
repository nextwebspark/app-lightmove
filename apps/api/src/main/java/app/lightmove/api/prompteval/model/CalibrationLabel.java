package app.lightmove.api.prompteval.model;

import app.lightmove.api.prompteval.constant.JudgePick;
import java.util.UUID;

/** A person's pick on one pair and criterion. */
public record CalibrationLabel(UUID pairId, String criterionKey, JudgePick pick) {}
