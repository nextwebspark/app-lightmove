package app.lightmove.api.candidate.model;

import java.util.List;

/** What the AI proposes for an assessment: a score and its evidence per competency, and the written report. */
public record AiScorecardDraft(List<CriterionScore> scores, ScorecardReport report) {}
