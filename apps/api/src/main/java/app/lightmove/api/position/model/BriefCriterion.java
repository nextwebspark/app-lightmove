package app.lightmove.api.position.model;

import app.lightmove.api.common.constant.CriterionMode;

/** One selection criterion of a brief, as another feature reads it. */
public record BriefCriterion(String text, CriterionMode mode) {}
