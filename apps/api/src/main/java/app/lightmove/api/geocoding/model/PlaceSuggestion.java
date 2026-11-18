package app.lightmove.api.geocoding.model;

import app.lightmove.api.geocoding.constant.PlaceKind;
import org.springframework.modulith.NamedInterface;

/** A place a location box may offer: {@code label} is what is shown, {@code value} what a search sends. */
@NamedInterface(name = "api", propagate = false)
public record PlaceSuggestion(String label, String value, PlaceKind kind) {}
