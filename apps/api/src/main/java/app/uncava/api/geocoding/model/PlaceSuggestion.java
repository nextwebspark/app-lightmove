package app.uncava.api.geocoding.model;

import app.uncava.api.geocoding.constant.PlaceKind;

/** A place a location box may offer: {@code label} is what is shown, {@code value} what a search sends. */
public record PlaceSuggestion(String label, String value, PlaceKind kind) {}
