package app.lightmove.api.project.dto;

/** What archiving the position would touch, read for the warning before anyone confirms. */
public record ProjectArchivePreviewResponse(long liveOutreachRuns, long openTasks) {}
