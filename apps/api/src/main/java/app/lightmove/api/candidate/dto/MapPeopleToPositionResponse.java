package app.lightmove.api.candidate.dto;

/** How many of the people named were added, and how many the position already held and kept as they were. */
public record MapPeopleToPositionResponse(int added, int alreadyIn) {}
