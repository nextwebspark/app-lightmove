package app.lightmove.api.strategy.dto;

import app.lightmove.api.strategy.constant.SearchVisibility;
import java.time.Instant;
import java.util.UUID;

/** Carries its filter, so loading one is a client-side apply rather than a second round trip. */
public record SavedSearchResponse(UUID id, String name, StrategyFilterDto filter,
                                  SearchVisibility visibility, UUID createdById, String createdByName,
                                  Instant createdAt, Instant updatedAt) {}
