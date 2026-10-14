package app.lightmove.api.position.model;

import app.lightmove.api.common.constant.CompetencyPanel;

/** One weighted competency of a brief, as another feature reads it: no provenance, no editing concerns. */
public record BriefCompetency(CompetencyPanel panel, String name, String description, int weight) {}
