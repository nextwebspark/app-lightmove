package app.lightmove.api.strategy.dto;

import java.util.List;

/** What the Company Keywords box offers, with the slice of the universe each keyword reaches. */
public record KeywordSuggestionsResponse(List<FacetCount> keywords) {}
