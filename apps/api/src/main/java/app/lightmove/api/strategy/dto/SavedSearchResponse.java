package app.lightmove.api.strategy.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One saved search in the toolbar's dropdown. The filter travels with it so that loading one is a
 * client-side apply followed by the ordinary autosave, rather than a second round trip.
 */
public record SavedSearchResponse(UUID id, String name, StrategyFilterDto filter, Instant createdAt) {}
