package app.lightmove.api.geocoding.model;

import app.lightmove.api.geocoding.constant.PlaceKind;

/** A place a location box may offer: {@code label} is what is shown, {@code value} what a search sends. */
public record PlaceSuggestion(String label, String value, PlaceKind kind) {}
