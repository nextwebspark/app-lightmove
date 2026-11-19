package app.lightmove.api.position.model;

import java.util.List;
import org.springframework.modulith.NamedInterface;

/**
 * What a candidate is scored against: the brief's competencies in their order, and how much of the assessment the
 * technical panel carries (the behavioural panel the rest).
 */
@NamedInterface(name = "api", propagate = false)
public record BriefCompetencies(List<BriefCompetency> competencies, int technicalShare) {}
