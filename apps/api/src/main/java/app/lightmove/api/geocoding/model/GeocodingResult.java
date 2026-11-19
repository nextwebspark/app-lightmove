package app.lightmove.api.geocoding.model;

import java.util.Map;
import org.springframework.modulith.NamedInterface;

/**
 * One resolve's points; {@code pending} counts places left unasked (budget spent or vendor down), so a
 * screen comes back for the rest rather than believe the map complete.
 */
@NamedInterface(name = "api", propagate = false)
public record GeocodingResult(Map<PlaceKey, GeoPoint> points, int pending) {}
