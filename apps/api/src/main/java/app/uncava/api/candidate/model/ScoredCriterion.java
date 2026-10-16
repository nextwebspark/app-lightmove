package app.uncava.api.candidate.model;

import app.uncava.api.common.constant.CompetencyPanel;

/**
 * A criterion with what it is worth and what it scored.
 *
 * @param weightInPanel its weight as a share of its panel's, 0–100
 * @param maxPoints     what a 10 would earn, out of the assessment's 100
 */
public record ScoredCriterion(
        CompetencyPanel panel,
        String name,
        String description,
        int weight,
        double weightInPanel,
        double maxPoints,
        Integer score,
        double points,
        String evidence
) {}
