package app.lightmove.api.enrichment.common.service;

import java.util.Map;

/**
 * The Search API request envelope shared by the people and company lookups; the filtered field
 * ({@code linkedin_id} vs {@code id}) is the caller's.
 */
public final class BrightDataSearch {

    /**
     * The vendor refuses a logical group of five rules ({@code Filter validation failed}, probed live),
     * so every {@code or} here holds four at most.
     */
    public static final int MAX_RULES_PER_GROUP = 4;

    /** Probed with a thousand slugs and answered in under three seconds; past this the oldest go unexcluded. */
    public static final int MAX_EXCLUDED = 1_000;

    private BrightDataSearch() {
    }

    public static Map<String, Object> exactlyOneWhere(String field, String value) {
        return Map.of("size", 1, "filter", Map.of("name", field, "operator", "=", "value", value));
    }
}
