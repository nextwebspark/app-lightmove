package app.lightmove.api.universe.model;

/** One grouped aggregate over a scope: a label and how many scoped companies carry it. */
public record ScopeBreakdown(String label, long count) {}
