package app.lightmove.api.project.dto;

/** One labelled slice of the scoped universe — a sector, a country, a city, a match tier. */
public record BreakdownDto(String label, long count) {}
