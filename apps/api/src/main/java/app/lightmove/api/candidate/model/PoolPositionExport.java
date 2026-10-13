package app.lightmove.api.candidate.model;

/** One position a person is in, as an export names it: its title and the status's label in the catalog. */
public record PoolPositionExport(String positionTitle, String statusLabel) {}
