package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Search and AI, unpriced — {@code lightmove.billing.fair-use.*}. Each {@code *-per-seat} is a month's ceiling of
 * units per staff seat, set far above what anyone doing their job reaches; {@code 0} lifts it. Each
 * {@code *-cost-fils} is a unit's estimated vendor cost, recorded for the margin report only.
 */
public record FairUseSettings(
        @DefaultValue("20000") long peopleSearchPagePerSeat,
        @DefaultValue("4") long peopleSearchPageCostFils,
        @DefaultValue("2000") long sourcingRunPerSeat,
        @DefaultValue("8") long sourcingRunCostFils,
        @DefaultValue("2000") long aiEnrichPerSeat,
        @DefaultValue("6") long aiEnrichCostFils,
        @DefaultValue("5000") long outreachOpenerPerSeat,
        @DefaultValue("2") long outreachOpenerCostFils,
        @DefaultValue("1500") long assistantAskPerSeat,
        @DefaultValue("15") long assistantAskCostFils
) {}
