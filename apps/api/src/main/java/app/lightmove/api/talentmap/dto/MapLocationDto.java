package app.lightmove.api.talentmap.dto;

import app.lightmove.api.geocoding.constant.GeoPrecision;

/**
 * Where one row sits. {@code placeLabel} is the place as it will be read back ("Riyadh, Saudi
 * Arabia"), so the popup need not rebuild it.
 *
 * <p>{@code country} and {@code countryCode} are that same place's country, stated apart from the
 * label because the screen groups by it: a company follows its people, so the country it is drawn in
 * is not the country its snapshot carries. Both are null where the place has no country the catalog
 * knows.
 */
public record MapLocationDto(double latitude, double longitude, GeoPrecision precision,
                             String placeLabel, String country, String countryCode) {}
