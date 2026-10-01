package app.lightmove.api.candidate.dto;

import java.util.List;

/** A page of history, newest first; {@code nextCursor} is the {@code before} for the next page. */
public record PersonTimelineResponse(List<PersonTimelineEntryResponse> entries, Long nextCursor) {}
