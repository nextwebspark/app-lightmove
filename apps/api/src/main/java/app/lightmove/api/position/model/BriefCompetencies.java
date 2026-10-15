package app.lightmove.api.position.model;

import java.util.List;

/**
 * What a candidate is scored against: the brief's competencies in their order, and how much of the assessment the
 * technical panel carries (the behavioural panel the rest).
 */
public record BriefCompetencies(List<BriefCompetency> competencies, int technicalShare) {}
