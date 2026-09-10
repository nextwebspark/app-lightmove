package app.lightmove.api.talentmap.dto;

import app.lightmove.api.geocoding.constant.GeoPrecision;

/**
 * Where one row sits. {@code placeLabel} is the place as it will be read back ("Riyadh, Saudi
 * Arabia"), so the popup need not rebuild it.
 */
public record MapLocationDto(double latitude, double longitude, GeoPrecision precision, String placeLabel) {}
