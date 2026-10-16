package app.uncava.api.talentmap.dto;

import app.uncava.api.geocoding.constant.GeoPrecision;

/**
 * Where one row sits. {@code country} is stated apart from the label because the screen groups by it
 * and a company is drawn where its people are; null where the catalog knows no country.
 */
public record MapLocationDto(double latitude, double longitude, GeoPrecision precision,
                             String placeLabel, String country, String countryCode) {}
