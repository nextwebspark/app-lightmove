package app.lightmove.api.screentext.service;

import app.lightmove.api.screentext.constant.ScreenTextLocale;
import app.lightmove.api.screentext.dto.ScreenTextBundle;
import app.lightmove.api.screentext.repository.ScreenTextReleaseRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The published bundle every page load asks for, held a minute per instance: a publish on another instance reaches
 * this one within that minute, and this instance's own publish evicts it at once.
 */
@Component
public class ScreenTextBundles {

    static final Duration TTL = Duration.ofSeconds(60);

    private final ScreenTextReleaseRepository releases;
    private final ScreenTextCatalog catalog;
    private final Cache<ScreenTextLocale, ScreenTextBundle> published;

    @Autowired
    public ScreenTextBundles(ScreenTextReleaseRepository releases, ScreenTextCatalog catalog) {
        this(releases, catalog, Ticker.systemTicker());
    }

    ScreenTextBundles(ScreenTextReleaseRepository releases, ScreenTextCatalog catalog, Ticker ticker) {
        this.releases = releases;
        this.catalog = catalog;
        this.published = Caffeine.newBuilder().expireAfterWrite(TTL).ticker(ticker).build();
    }

    public ScreenTextBundle published(ScreenTextLocale locale) {
        return published.get(locale, this::read);
    }

    public ScreenTextBundle of(ScreenTextLocale locale, int release, Map<String, String> overrides) {
        return new ScreenTextBundle(locale.tag(), release, "r%d-%s".formatted(release, catalog.fingerprint()),
                catalog.servable(overrides));
    }

    public void evict(ScreenTextLocale locale) {
        published.invalidate(locale);
    }

    private ScreenTextBundle read(ScreenTextLocale locale) {
        return releases.findFirstByLocaleOrderByNumberDesc(locale.tag())
                .map(release -> of(locale, release.getNumber(), release.getEntries()))
                .orElseGet(() -> of(locale, 0, Map.of()));
    }
}
