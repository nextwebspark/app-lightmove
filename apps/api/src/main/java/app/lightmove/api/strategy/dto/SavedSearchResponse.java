package app.lightmove.api.strategy.dto;

import app.lightmove.api.strategy.constant.SearchVisibility;
import java.time.Instant;
import java.util.UUID;

/**
 * One saved search in the toolbar's dropdown. The filter travels with it so that loading one is a
 * client-side apply followed by the ordinary autosave, rather than a second round trip.
 *
 * <p>The author travels with it too: the dropdown splits into the caller's own searches and the
 * mandate's shared ones, and a shared row is only useful if it says whose it is.
 */
public record SavedSearchResponse(UUID id, String name, StrategyFilterDto filter,
                                  SearchVisibility visibility, UUID createdById, String createdByName,
                                  Instant createdAt, Instant updatedAt) {}
