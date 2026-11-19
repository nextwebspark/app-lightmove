package app.lightmove.api.geocoding.model;

import app.lightmove.api.geocoding.constant.GeoPrecision;
import org.springframework.modulith.NamedInterface;

/** Where a place is, and how closely. WGS84 degrees, latitude first as a person would say it. */
@NamedInterface(name = "api", propagate = false)
public record GeoPoint(double latitude, double longitude, GeoPrecision precision) {}
