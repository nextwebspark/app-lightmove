package app.lightmove.api.enrichment.common.service;

import java.util.Map;

/**
 * The request body Bright Data's synchronous Search API takes, shared by the people and company
 * lookups because it is one API asked two questions. Which field is filtered differs — the people
 * dataset keys on {@code linkedin_id}, the company dataset on {@code id} — so the field is the
 * caller's, and only the envelope is here.
 */
public final class BrightDataSearch {

    private BrightDataSearch() {
    }

    public static Map<String, Object> exactlyOneWhere(String field, String value) {
        return Map.of("size", 1, "filter", Map.of("name", field, "operator", "=", "value", value));
    }
}
