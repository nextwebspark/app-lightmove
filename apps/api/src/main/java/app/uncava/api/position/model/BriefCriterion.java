package app.uncava.api.position.model;

import app.uncava.api.common.constant.CriterionMode;

/** One selection criterion of a brief, as another feature reads it. */
public record BriefCriterion(String text, CriterionMode mode) {}
