package app.lightmove.api.prompteval.model;

import app.lightmove.api.prompteval.constant.EvalCaseSplit;
import java.util.UUID;

/** A run about to be queued on the cases its dataset has in use in {@code split}. */
public record NewEvalRun(String promptId, UUID datasetId, EvalCaseSplit split, int candidateVersion,
                         int baselineVersion, int repeats, boolean gateReady, int callCap, UUID startedBy) {}
