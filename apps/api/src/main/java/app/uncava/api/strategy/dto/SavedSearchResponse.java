package app.uncava.api.strategy.dto;

import app.uncava.api.strategy.constant.SearchKind;
import app.uncava.api.strategy.constant.SearchVisibility;
import java.time.Instant;
import java.util.UUID;

/** Carries its filter — the people one on a PEOPLE search — so loading one is a client-side apply. */
public record SavedSearchResponse(UUID id, String name, SearchKind kind, StrategyFilterDto filter,
                                  PeopleFilterDto peopleFilter, SearchVisibility visibility, UUID createdById, String createdByName,
                                  Instant createdAt, Instant updatedAt) {}
