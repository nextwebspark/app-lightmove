package app.uncava.api.position.dto;

import app.uncava.api.common.constant.CriterionMode;
import app.uncava.api.position.constant.FieldSource;

/** One selection criterion as the brief returns it. */
public record CriterionResponse(String text, CriterionMode mode, FieldSource source) {}
