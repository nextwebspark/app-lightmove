package app.lightmove.api.positiontemplate.model;

import app.lightmove.api.common.constant.CriterionMode;
import org.springframework.modulith.NamedInterface;

/**
 * One assessment criterion a template drafts. Deliberately not the brief's {@code PositionCriterion}: that one is
 * a Hibernate embeddable carrying {@code source}, and a template row has no say in that field —
 * everything it drafts is {@code TEMPLATE} by definition, and the applier stamps it.
 */
@NamedInterface(name = "api", propagate = false)
public record PositionTemplateCriterion(String text, CriterionMode mode) {
}
