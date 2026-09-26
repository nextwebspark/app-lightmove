package app.lightmove.api.report.dto;

/** WGS84 degrees. Always a country centroid: the pin marks a market, not where anybody works. */
public record MapPointDto(double latitude, double longitude) {}
