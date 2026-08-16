package app.lightmove.api.company.dto;

import java.util.List;

/**
 * Suggestions for a set of chosen (direct) sectors: adjacent sectors from the curated map and
 * inferred tags computed live from tag co-occurrence in the company universe.
 */
public record SuggestionsResponse(List<String> adjacent, List<TagCount> inferredTags) {}
