package app.lightmove.api.report.dto;

/** A gender split counted only from rows where a researcher recorded one. */
public record GenderSplitDto(int female, int male, int other) {}
