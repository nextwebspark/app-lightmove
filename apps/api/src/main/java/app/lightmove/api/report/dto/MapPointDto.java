package app.lightmove.api.report.dto;

/**
 * Where a hub sits, so the chapter can draw it. WGS84 degrees, latitude first as a person would say
 * it.
 *
 * <p>Always a country centroid, because a hub is a country — the pin marks the market, not an
 * address, and nothing here should be read as where anybody actually works.
 */
public record MapPointDto(double latitude, double longitude) {}
