package app.lightmove.api.company.dto;

/** One industry tag and how many companies in the queried sectors carry it. */
public record TagCount(String tag, long count) {}
