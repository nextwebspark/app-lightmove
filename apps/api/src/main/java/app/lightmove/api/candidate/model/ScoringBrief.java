package app.lightmove.api.candidate.model;

import java.util.List;
import org.springframework.modulith.NamedInterface;

/** The brief's competencies and technical share, as every assessment is scored against them. */
@NamedInterface(name = "api", propagate = false)
public record ScoringBrief(List<ScorecardCriterion> criteria, int technicalShare) {}
