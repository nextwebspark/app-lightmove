package app.lightmove.api.report.dto;

/**
 * Where a hub sits, so the chapter can draw it. WGS84 degrees, latitude first as a person would say
 * it, and {@code cityPrecision} false where only the country could be placed — a country centroid is
 * a whole market drawn as one dot, which the screen says out loud rather than pretending otherwise.
 */
public record MapPointDto(double latitude, double longitude, boolean cityPrecision) {}
