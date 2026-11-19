package app.lightmove.api.candidate.model;

import java.util.List;
import org.springframework.modulith.NamedInterface;

/** What the AI proposes for an assessment: a score and its evidence per competency, and the written report. */
@NamedInterface(name = "api", propagate = false)
public record AiScorecardDraft(List<CriterionScore> scores, ScorecardReport report) {}
