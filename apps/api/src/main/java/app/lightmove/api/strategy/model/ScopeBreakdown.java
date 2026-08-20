package app.lightmove.api.strategy.model;

/**
 * One grouped aggregate over a scope: a label and how many scoped companies carry it. The label is
 * whatever the grouping expression produced — an industry, a country, a city, a match tier.
 */
public record ScopeBreakdown(String label, long count) {}
