package app.lightmove.api.geocoding.model;

import app.lightmove.api.geocoding.constant.GeoPrecision;

/** Where a place is, and how closely. WGS84 degrees, latitude first as a person would say it. */
public record GeoPoint(double latitude, double longitude, GeoPrecision precision) {}
