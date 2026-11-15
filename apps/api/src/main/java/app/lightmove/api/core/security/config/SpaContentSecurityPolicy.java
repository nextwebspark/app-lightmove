package app.lightmove.api.core.security.config;

import java.net.URI;

/**
 * The SPA's Content-Security-Policy: every third party the bundle may call is named here, so a new one is a reviewed
 * change (#929). Scripts are this origin only; styles allow inline because the rich-text editor and the booking
 * page's scheduler inject {@code <style>} elements, and images allow any https host for company logos and photos.
 */
final class SpaContentSecurityPolicy {

    /** Mapbox GL's styles, tiles and session calls; it builds its worker from a {@code blob:}. */
    private static final String MAPBOX = "https://api.mapbox.com https://events.mapbox.com https://*.tiles.mapbox.com";

    private SpaContentSecurityPolicy() {
    }

    static String of(String schedulerBaseUrl) {
        return "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                + "img-src 'self' data: blob: https:; font-src 'self' data:; "
                + "connect-src 'self' " + MAPBOX + " " + originOf(schedulerBaseUrl) + "; "
                // child-src: Safari before 15.5 reads a worker's source from it, not from worker-src.
                + "worker-src 'self' blob:; child-src blob:; frame-src 'self' blob:; "
                // No page of the app is ever framed, and the MCP consent screen's Allow must not be clickjacked.
                + "object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'";
    }

    // A blank NYLAS_BASE_URL slips past the binding default and would put "null://null" in the header.
    private static String originOf(String url) {
        URI uri = url == null || url.isBlank() ? null : URI.create(url.strip());
        if (uri == null || !uri.isAbsolute() || uri.getAuthority() == null) {
            throw new IllegalStateException(
                    "lightmove.outreach.nylas.base-url must be an absolute URL, so the SPA's policy can name it");
        }
        return uri.getScheme() + "://" + uri.getAuthority();
    }
}
