package app.lightmove.api.enrichment.peoplesearch.dto;

import app.lightmove.api.geocoding.model.PlaceSuggestion;
import java.util.List;

public record LocationSuggestionsResponse(List<PlaceSuggestion> places) {}
