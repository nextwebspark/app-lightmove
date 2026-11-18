package app.lightmove.api.candidate.model;

import org.springframework.modulith.NamedInterface;

/** One position a person is in, as an export names it: its title and the status's label in the catalog. */
@NamedInterface(name = "api", propagate = false)
public record PoolPositionExport(String positionTitle, String statusLabel) {}
