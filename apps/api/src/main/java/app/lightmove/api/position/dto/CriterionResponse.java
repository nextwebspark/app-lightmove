package app.lightmove.api.position.dto;

import app.lightmove.api.common.constant.CriterionMode;
import app.lightmove.api.position.constant.FieldSource;

/** One selection criterion as the brief returns it. */
public record CriterionResponse(String text, CriterionMode mode, FieldSource source) {}
