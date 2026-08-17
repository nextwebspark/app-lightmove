package app.lightmove.api.project.dto;

import app.lightmove.api.project.constant.CriterionMode;

/** One selection criterion as the brief returns it. */
public record CriterionResponse(String text, CriterionMode mode, boolean fromBrief) {}
