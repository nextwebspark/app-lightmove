package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How long the filter sidebar's counts may describe the load before last — {@code
 * lightmove.company.facets.*}.
 *
 * <p>The counts are the shape of the Apollo universe: the same for every user in every workspace, and
 * changed only when the pipeline reloads the table. V33 already makes this trade for the keyword
 * vocabulary, on the grounds that a stale <i>offer</i> narrows honestly; a chip counting the previous
 * load is the same bargain.
 *
 * <p>An hour, against a pipeline that loads daily. Well short of a load, so a count is never more than
 * an hour behind one, and long enough that a consultant's working session costs the database nothing.
 * Matching the load interval would save marginally more and put a whole day between a load and the
 * sidebar agreeing with it; the hour is the cheaper half of that trade.
 *
 * <p>One knob rather than two, because the browser's {@code max-age} is derived from it: a browser can
 * then never hold a figure staler than the server's own window. Zero switches both off, which is what
 * the test profile does — the suite reseeds the universe between tests and every call must re-read it.
 */
public record CompanyFacetsSettings(
        @DefaultValue("1h") Duration cacheTtl
) {

    public CompanyFacetsSettings {
        if (cacheTtl.isNegative()) {
            throw new IllegalArgumentException(
                    "lightmove.company.facets.cache-ttl must not be negative, but was " + cacheTtl);
        }
    }

    /** Whether anything is held at all; zero means every call recomputes and the browser stores nothing. */
    public boolean isEnabled() {
        return !cacheTtl.isZero();
    }
}
