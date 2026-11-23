package app.lightmove.api.prompteval.model;

import java.util.List;
import java.util.UUID;

/** A dataset's rubric at one version: the criteria in the order they are judged and listed. */
public record EvalRubric(UUID id, UUID datasetId, int version, List<RubricCriterion> criteria) {}
