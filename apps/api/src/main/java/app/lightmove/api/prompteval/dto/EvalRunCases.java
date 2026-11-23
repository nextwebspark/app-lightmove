package app.lightmove.api.prompteval.dto;

import java.util.List;

/**
 * The cases of a finished run that changed, regressions first, and how many did not. Both empty for a run that
 * gave no result.
 */
public record EvalRunCases(List<EvalRunCaseComparison> changed, int unchanged) {
}
