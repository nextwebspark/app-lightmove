package app.lightmove.api.core.security.config;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.NylasSettings;
import app.lightmove.api.core.config.OutreachSettings;
import java.net.URI;

/**
 * The SPA's Content-Security-Policy: every third party the bundle may reach is named here, so a new one is a reviewed
 * change (#929). Scripts are this origin only; styles allow inline because the rich-text editor and the booking
 * page's scheduler inject {@code <style>} elements, and images allow any https host for company logos and photos.
 */
final class SpaContentSecurityPolicy {

    /** Mapbox GL's styles, tiles and session calls; it builds its worker from a {@code blob:}. */
    private static final String MAPBOX = "https://api.mapbox.com https://events.mapbox.com https://*.tiles.mapbox.com";

    private SpaContentSecurityPolicy() {
    }

    static String of(LightMoveProperties properties) {
        OutreachSettings outreach = properties.outreach();
        NylasSettings nylas = outreach == null ? null : outreach.nylas();
        String scheduler = nylas == null ? "" : " " + originOf(nylas.baseUrl());
        return "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                + "img-src 'self' data: blob: https:; font-src 'self' data:; "
                + "connect-src 'self' " + MAPBOX + scheduler + "; "
                + "worker-src 'self' blob:; child-src blob:; frame-src 'self' blob:; "
                // No page of the app is ever framed, and the MCP consent screen's Allow must not be clickjacked.
                + "object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'";
    }

    private static String originOf(String url) {
        URI uri = URI.create(url);
        return uri.getScheme() + "://" + uri.getAuthority();
    }
}
