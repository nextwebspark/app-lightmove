package app.lightmove.api.company.dto;

/** One sector (a primary_industry value) and how many companies carry it. */
public record SectorCount(String name, long count) {}
