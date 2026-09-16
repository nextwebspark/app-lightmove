package app.lightmove.api.report.dto;

/**
 * One seniority level's gender split, counted only from rows where a researcher recorded it.
 *
 * <p>The three add up to the level's <i>recorded</i> population, never to the level itself: everyone
 * with no gender on file is in {@code genderUnrecorded} on the chapter, so a level nobody has
 * recorded reads as unmeasured rather than as empty.
 */
public record GenderLevelRowDto(String level, int female, int male, int other) {}
