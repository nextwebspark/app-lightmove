package app.lightmove.api.candidate.dto;

/**
 * How many of the people named were added, how many the position already held and kept as they were, and how many
 * the workspace's suppression list left out.
 */
public record MapPeopleToPositionResponse(int added, int alreadyIn, int suppressed) {}
