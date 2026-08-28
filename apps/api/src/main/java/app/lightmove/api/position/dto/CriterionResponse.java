package app.lightmove.api.position.dto;

import app.lightmove.api.position.constant.CriterionMode;

/** One selection criterion as the brief returns it. */
public record CriterionResponse(String text, CriterionMode mode, boolean fromBrief) {}
