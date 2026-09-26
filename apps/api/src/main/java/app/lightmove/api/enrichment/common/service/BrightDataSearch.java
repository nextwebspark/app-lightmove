package app.lightmove.api.enrichment.common.service;

import java.util.Map;

/**
 * The Search API request envelope shared by the people and company lookups; the filtered field
 * ({@code linkedin_id} vs {@code id}) is the caller's.
 */
public final class BrightDataSearch {

    private BrightDataSearch() {
    }

    public static Map<String, Object> exactlyOneWhere(String field, String value) {
        return Map.of("size", 1, "filter", Map.of("name", field, "operator", "=", "value", value));
    }
}
