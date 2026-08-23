package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The in-process caches over the company universe ({@code app_lm_apollo_companies}).
 *
 * <p>Every read they hold is a pure function of its arguments over ETL-owned reference data that the
 * application never writes, so the only event that invalidates any of them is the pipeline reloading
 * the universe. The TTLs below are the backstop for that; {@code UniverseFingerprint} is what actually
 * notices, and {@code reloadCheckInterval} is how often it is allowed to look.
 *
 * <p>The maximum sizes are entry counts. Every cache here has a partly caller-supplied key — the
 * typeahead's query, and the scope's {@code nameQuery} — so all of them are bounded, or they would be
 * slow memory leaks fed by whoever is typing.
 */
public record CompanyCacheSettings(

        /** Master switch. Off means every read goes to Postgres, exactly as it did before caching. */
        @DefaultValue("true") boolean enabled,

        /**
         * How long the five filter accordions may go unrefreshed. Long, because the counts change only
         * on a pipeline reload and {@code UniverseFingerprint} catches that far sooner than this does.
         */
        @DefaultValue("6h") Duration facetsTtl,

        /** How long a company-picker suggestion list may go unrefreshed. */
        @DefaultValue("1h") Duration typeaheadTtl,

        /** Distinct typeahead queries held at once. See the class doc on why this must be bounded. */
        @DefaultValue("2000") int typeaheadMaxEntries,

        /** How long a filtered count or page may go unrefreshed. */
        @DefaultValue("1h") Duration scopeTtl,

        /** Distinct scopes whose total is held. A {@code Long} per entry, so this can be generous. */
        @DefaultValue("5000") int scopeCountMaxEntries,

        /** Distinct (scope, sort, page) combinations held. The heaviest entries here — keep it lower. */
        @DefaultValue("1000") int scopePageMaxEntries,

        /**
         * The shortest gap between two checks for a reloaded universe. Not a schedule: the check rides
         * a request, so this is a ceiling on how often traffic may trigger one, not a promise that one
         * happens.
         */
        @DefaultValue("2m") Duration reloadCheckInterval
) {

    /**
     * The ceiling on every {@code *MaxEntries} value, enforced at startup rather than found as an
     * OutOfMemoryError. The page cache sets it: one entry holds up to {@code max-page-size} (100)
     * {@code CompanyRow}s carrying {@code short_description}, against ~768 MB of heap
     * ({@code -XX:MaxRAMPercentage=75.0} on a 1 GiB container) at 80 concurrent requests.
     */
    public static final int MAX_CACHE_ENTRIES = 50_000;

    public CompanyCacheSettings {
        requireEntryCount("typeahead-max-entries", typeaheadMaxEntries);
        requireEntryCount("scope-count-max-entries", scopeCountMaxEntries);
        requireEntryCount("scope-page-max-entries", scopePageMaxEntries);
        requirePositive("facets-ttl", facetsTtl);
        requirePositive("typeahead-ttl", typeaheadTtl);
        requirePositive("scope-ttl", scopeTtl);
        requirePositive("reload-check-interval", reloadCheckInterval);
    }

    private static void requireEntryCount(String key, int value) {
        if (value < 1 || value > MAX_CACHE_ENTRIES) {
            throw new IllegalArgumentException("lightmove.company.cache." + key
                    + " must be between 1 and " + MAX_CACHE_ENTRIES + ", but was " + value);
        }
    }

    /** Zero would build caches that hold nothing; the switch for "never cache" is {@link #enabled}. */
    private static void requirePositive(String key, Duration value) {
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("lightmove.company.cache." + key
                    + " must be positive, but was " + value);
        }
    }
}
