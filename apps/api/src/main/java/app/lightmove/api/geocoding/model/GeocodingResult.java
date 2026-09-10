package app.lightmove.api.geocoding.model;

import java.util.Map;

/**
 * What one resolve answered, and how much it could not yet. {@code pending} counts places left unasked
 * because the read's vendor budget ran out or the vendor was unreachable — the caller reports it so a
 * screen can come back for the rest rather than believe the map complete.
 */
public record GeocodingResult(Map<PlaceKey, GeoPoint> points, int pending) {}
