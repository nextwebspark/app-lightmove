package app.lightmove.api.prompteval.dto;

import java.util.List;

/** A dataset, its cases (those in use first, the retired ones after them) and its rubric, null where it has none. */
public record EvalDatasetDetail(EvalDatasetOverview dataset, List<EvalCaseSummary> cases, EvalRubricDetail rubric) {
}
