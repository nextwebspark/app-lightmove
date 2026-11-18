package app.lightmove.api.position.model;

import app.lightmove.api.common.constant.CompetencyPanel;
import org.springframework.modulith.NamedInterface;

/** One weighted competency of a brief, as another feature reads it: no provenance, no editing concerns. */
@NamedInterface(name = "api", propagate = false)
public record BriefCompetency(CompetencyPanel panel, String name, String description, int weight) {}
