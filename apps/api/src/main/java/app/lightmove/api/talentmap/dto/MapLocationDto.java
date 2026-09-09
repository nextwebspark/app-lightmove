package app.lightmove.api.talentmap.dto;

/**
 * Where one row sits. {@code precision} is {@code CITY} or {@code COUNTRY}; {@code placeLabel} is the
 * place as it will be read back ("Riyadh, Saudi Arabia"), so the popup need not rebuild it.
 */
public record MapLocationDto(double latitude, double longitude, String precision, String placeLabel) {}
