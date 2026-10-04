package app.lightmove.api.candidate.model;

/** One position a person is in, as an export names it: its title and a {@code CandidateStatus} wire token. */
public record PoolPositionExport(String positionTitle, String status) {}
