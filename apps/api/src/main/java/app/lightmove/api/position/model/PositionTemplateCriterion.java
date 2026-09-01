package app.lightmove.api.position.model;

import app.lightmove.api.position.constant.CriterionMode;

/**
 * One assessment criterion a template drafts. Deliberately not {@link PositionCriterion}: that one is
 * a Hibernate embeddable carrying {@code fromBrief}, and a template row has no say in that flag —
 * everything it drafts is from the brief by definition, and the applier stamps it.
 */
public record PositionTemplateCriterion(String text, CriterionMode mode) {
}
