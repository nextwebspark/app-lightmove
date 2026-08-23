package app.lightmove.api.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The cache bounds, enforced when the settings bind rather than when the heap runs out.
 *
 * <p>Every value here is reachable from an environment variable on a 1 GiB container, so each of
 * these is a deploy that fails loudly instead of an instance that dies under load.
 */
class CompanyCacheSettingsTest {

    private static final Duration TTL = Duration.ofHours(1);

    private static CompanyCacheSettings with(int typeahead, int scopeCount, int scopePage) {
        return new CompanyCacheSettings(true, TTL, TTL, typeahead, TTL, scopeCount, scopePage,
                Duration.ofMinutes(2));
    }

    @Test
    @DisplayName("an entry count past the ceiling is refused, naming the key and the maximum")
    void refusesAnEntryCountPastTheCeiling() {
        assertThatThrownBy(() -> with(CompanyCacheSettings.MAX_CACHE_ENTRIES + 1, 5_000, 1_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("typeahead-max-entries")
                .hasMessageContaining(String.valueOf(CompanyCacheSettings.MAX_CACHE_ENTRIES));
    }

    @Test
    @DisplayName("the page cache is bounded too — it holds the heaviest entries")
    void refusesAnUnboundedPageCache() {
        assertThatThrownBy(() -> with(2_000, 5_000, CompanyCacheSettings.MAX_CACHE_ENTRIES + 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scope-page-max-entries");
    }

    @Test
    @DisplayName("zero entries is refused — the switch for 'no caching' is enabled=false")
    void refusesAZeroEntryCount() {
        assertThatThrownBy(() -> with(0, 5_000, 1_000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a zero TTL is refused rather than read as 'never cache'")
    void refusesAZeroTtl() {
        // Caffeine treats a zero expireAfterWrite as evict-immediately, so the caches would exist,
        // report themselves present, and hold nothing. That is a silent no-op, not a configuration.
        assertThatThrownBy(() -> new CompanyCacheSettings(true, Duration.ZERO, TTL, 2_000, TTL,
                5_000, 1_000, Duration.ofMinutes(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("facets-ttl");
    }

    @Test
    @DisplayName("a negative reload interval is refused")
    void refusesANegativeReloadInterval() {
        assertThatThrownBy(() -> new CompanyCacheSettings(true, TTL, TTL, 2_000, TTL, 5_000, 1_000,
                Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reload-check-interval");
    }

    @Test
    @DisplayName("a valid configuration binds")
    void acceptsAValidConfiguration() {
        CompanyCacheSettings settings = with(2_000, 5_000, 1_000);
        assertThat(settings.enabled()).isTrue();
        assertThat(settings.typeaheadMaxEntries()).isEqualTo(2_000);
        assertThat(settings.reloadCheckInterval()).isEqualTo(Duration.ofMinutes(2));
    }
}
