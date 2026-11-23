package app.lightmove.api.prompteval.dto;

import app.lightmove.api.prompteval.constant.RubricState;

/** A dataset's rubric as the list shows it. */
public record EvalRubricSummary(int version, RubricState state) {
}
