package app.lightmove.api.notetaker.dto;

import app.lightmove.api.common.constant.CompetencyPanel;

/** One competency of the brief as the plan groups questions under it; {@code evidenced}: the scorecard shows it. */
public record PlanCompetencyResponse(CompetencyPanel panel, String name, int weight, boolean evidenced) {}
