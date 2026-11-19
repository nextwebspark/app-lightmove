package app.lightmove.api.positiontemplate.model;

import app.lightmove.api.common.constant.CompetencyPanel;
import org.springframework.modulith.NamedInterface;

/**
 * One weighted competency a template drafts, in the panel it belongs to. The library's own rows are
 * balanced so each panel totals 100, which is what makes a seeded brief read as ready — but nothing
 * enforces it here, for the same reason the brief does not: a panel mid-rebalance must be storable.
 */
@NamedInterface(name = "api", propagate = false)
public record PositionTemplateCompetency(CompetencyPanel panel, String name, String description,
                                         int weight) {
}
