package app.lightmove.api.candidate.dto;

/** How many people a bulk change actually changed; the rest already read that way. */
public record BulkPeopleChangeResponse(int changed) {}
