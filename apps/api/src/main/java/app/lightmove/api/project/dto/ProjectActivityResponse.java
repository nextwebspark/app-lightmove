package app.lightmove.api.project.dto;

import java.util.List;

/** A page of a mandate's activity, newest first. {@code nextCursor} is null on the last page. */
public record ProjectActivityResponse(List<ProjectActivityEntryResponse> entries, Long nextCursor) {}
