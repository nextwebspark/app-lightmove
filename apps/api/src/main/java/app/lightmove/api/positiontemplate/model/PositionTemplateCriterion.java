package app.lightmove.api.positiontemplate.model;

import app.lightmove.api.common.constant.CriterionMode;

/**
 * One assessment criterion a template drafts. Deliberately not the brief's {@code PositionCriterion}: that one is
 * a Hibernate embeddable carrying {@code fromBrief}, and a template row has no say in that flag —
 * everything it drafts is from the brief by definition, and the applier stamps it.
 */
public record PositionTemplateCriterion(String text, CriterionMode mode) {
}
