package app.lightmove.api.strategy.model;

/** One grouped aggregate over a scope: a label and how many scoped companies carry it. */
public record ScopeBreakdown(String label, long count) {}
