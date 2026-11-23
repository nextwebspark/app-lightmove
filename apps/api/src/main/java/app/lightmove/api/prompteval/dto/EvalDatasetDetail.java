package app.lightmove.api.prompteval.dto;

import java.util.List;

/** A dataset and its cases: those in use first, the retired ones after them. */
public record EvalDatasetDetail(EvalDatasetOverview dataset, List<EvalCaseSummary> cases) {
}
