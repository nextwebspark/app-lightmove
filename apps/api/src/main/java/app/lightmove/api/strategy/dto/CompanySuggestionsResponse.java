package app.lightmove.api.strategy.dto;

import java.util.List;

/** A picker's typeahead results, wrapped so the response can grow without breaking its shape. */
public record CompanySuggestionsResponse(List<CompanySuggestion> companies) {}
