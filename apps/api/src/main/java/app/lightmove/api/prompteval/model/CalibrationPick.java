package app.lightmove.api.prompteval.model;

import app.lightmove.api.prompteval.constant.JudgePick;
import java.util.UUID;

/** The judge's pick on one labelled pair and criterion, in the pair's own A and B. Null: none it could be read. */
public record CalibrationPick(UUID pairId, String criterionKey, JudgePick pick, String reason) {}
