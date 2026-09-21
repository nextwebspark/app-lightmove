package app.lightmove.api.positiontemplate.model;

import app.lightmove.api.common.constant.CriterionMode;

/**
 * One assessment criterion a template drafts. Deliberately not the brief's {@code PositionCriterion}: that one is
 * a Hibernate embeddable carrying {@code source}, and a template row has no say in that field —
 * everything it drafts is {@code TEMPLATE} by definition, and the applier stamps it.
 */
public record PositionTemplateCriterion(String text, CriterionMode mode) {
}
