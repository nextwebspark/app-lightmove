package app.lightmove.api.candidate.model;

import java.util.List;

/** The brief's competencies and technical share, as every assessment is scored against them. */
public record ScoringBrief(List<ScorecardCriterion> criteria, int technicalShare) {}
