package app.uncava.api.enrichment.peoplesearch.dto;

import app.uncava.api.geocoding.model.PlaceSuggestion;
import java.util.List;

public record LocationSuggestionsResponse(List<PlaceSuggestion> places) {}
