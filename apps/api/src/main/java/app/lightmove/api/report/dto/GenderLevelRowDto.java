package app.lightmove.api.report.dto;

/**
 * One level's gender split over its <i>recorded</i> population only; the unrecorded are counted apart
 * on the chapter, so a level nobody recorded reads as unmeasured rather than empty.
 */
public record GenderLevelRowDto(String level, int female, int male, int other) {}
