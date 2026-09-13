package app.lightmove.api.report.dto;

/** One sector × seniority pair and how many executives sit in it. */
public record MarketCellDto(String sector, String level, int count) {}
