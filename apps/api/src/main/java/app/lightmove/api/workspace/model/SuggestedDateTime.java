package app.lightmove.api.workspace.model;

import app.lightmove.api.core.time.model.DateTimePreferences;

/** What a workspace reads in until an admin chooses; {@code countryCode} is null where the standard stood in for it. */
public record SuggestedDateTime(DateTimePreferences preferences, String countryCode) {}
