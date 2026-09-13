package app.lightmove.api.report.dto;

import java.time.LocalDate;

/** Executives identified in one week of the mandate, named by the day the week ended on. */
public record WeeklyCountDto(LocalDate weekEnding, int identified) {}
