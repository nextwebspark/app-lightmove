package app.lightmove.api.triagecompany.dto;

/** The triage sub-nav's badge counts — how a mandate's triaged companies split across the three stages. */
public record TriageCountsDto(long inUniverse, long shortlisted, long declined) {}
