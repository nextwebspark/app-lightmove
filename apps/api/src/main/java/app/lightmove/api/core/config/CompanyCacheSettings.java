package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The in-process caches over the company universe.
 *
 * <p>Every key here is partly caller-supplied — the typeahead's query, the scope's {@code nameQuery} —
 * so every cache is bounded, or it is a slow memory leak fed by whoever is typing.
 */
public record CompanyCacheSettings(

        @DefaultValue("true") boolean enabled,

        @DefaultValue("6h") Duration facetsTtl,
        @DefaultValue("1h") Duration typeaheadTtl,
        @DefaultValue("2000") int typeaheadMaxEntries,
        @DefaultValue("1h") Duration scopeTtl,
        @DefaultValue("5000") int scopeCountMaxEntries,
        @DefaultValue("1000") int scopePageMaxEntries,

        /** A ceiling on how often traffic may trigger a reload check, not a promise of one. */
        @DefaultValue("2m") Duration reloadCheckInterval
) {

    public static final int MAX_CACHE_ENTRIES = 50_000;

    /** Lower: one page entry holds up to 100 rows, so 50,000 would exceed the ~768 MB heap. */
    public static final int MAX_PAGE_CACHE_ENTRIES = 5_000;

    public CompanyCacheSettings {
        requireEntryCount("typeahead-max-entries", typeaheadMaxEntries, MAX_CACHE_ENTRIES);
        requireEntryCount("scope-count-max-entries", scopeCountMaxEntries, MAX_CACHE_ENTRIES);
        requireEntryCount("scope-page-max-entries", scopePageMaxEntries, MAX_PAGE_CACHE_ENTRIES);
        requirePositive("facets-ttl", facetsTtl);
        requirePositive("typeahead-ttl", typeaheadTtl);
        requirePositive("scope-ttl", scopeTtl);
        requirePositive("reload-check-interval", reloadCheckInterval);
        if (reloadCheckInterval.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException(
                    "lightmove.company.cache.reload-check-interval must not exceed 1d, but was "
                            + reloadCheckInterval);
        }
    }

    private static void requireEntryCount(String key, int value, int ceiling) {
        if (value < 1 || value > ceiling) {
            throw new IllegalArgumentException("lightmove.company.cache." + key
                    + " must be between 1 and " + ceiling + ", but was " + value);
        }
    }

    private static void requirePositive(String key, Duration value) {
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("lightmove.company.cache." + key
                    + " must be positive, but was " + value);
        }
    }
}
