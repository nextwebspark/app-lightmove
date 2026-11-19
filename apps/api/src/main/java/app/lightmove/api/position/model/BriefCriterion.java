package app.lightmove.api.position.model;

import app.lightmove.api.common.constant.CriterionMode;
import org.springframework.modulith.NamedInterface;

/** One selection criterion of a brief, as another feature reads it. */
@NamedInterface(name = "api", propagate = false)
public record BriefCriterion(String text, CriterionMode mode) {}
