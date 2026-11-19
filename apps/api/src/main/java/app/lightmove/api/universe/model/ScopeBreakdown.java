package app.lightmove.api.universe.model;

import org.springframework.modulith.NamedInterface;

/** One grouped aggregate over a scope: a label and how many scoped companies carry it. */
@NamedInterface(name = "api", propagate = false)
public record ScopeBreakdown(String label, long count) {}
