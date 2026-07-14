package app.lightmove.api.common.config;

import java.io.IOException;
import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the built SPA out of the same application that serves the API.
 *
 * <p>Not a packaging shortcut — a consequence of the auth model. The refresh cookie is
 * {@code SameSite=Strict} and host-only, so the browser sends it back only to the host that served the
 * page. The SPA and the API therefore have to be one origin, and this is what makes them one. (It is
 * the same reason the Vite dev server proxies {@code /api} instead of pointing at :8080.)
 *
 * <p>The bundle is copied into {@code classpath:/static/} at image build time. It is <b>absent</b> in
 * local development, where Vite serves the SPA itself — so every method here has to behave sanely with
 * nothing to serve, and does: unresolved paths fall through to a 404, exactly as they did before.
 */
@Configuration
public class SpaResourceConfig implements WebMvcConfigurer {

    private static final String STATIC_ROOT = "classpath:/static/";
    private static final String INDEX = "/static/index.html";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Vite fingerprints everything under /assets/ with a content hash, so a given URL's bytes can
        // never change. Cache it for a year and never revalidate.
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(STATIC_ROOT + "assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());

        // index.html is the one file whose URL is stable while its contents change on every deploy. Let
        // a browser cache it and the user gets yesterday's shell asking for asset hashes that no longer
        // exist — a white screen that clears only on a hard refresh.
        registry.addResourceHandler("/**")
                .addResourceLocations(STATIC_ROOT)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new SpaFallbackResolver());
    }

    /**
     * Serves the requested file, or the SPA shell if there is no such file.
     *
     * <p>The fallback is what lets a user open {@code /auth/verify?token=…} straight out of their inbox.
     * That URL is a client-side route — there is no file at that path and never will be — so without
     * this it 404s, and the verification link in every signup email is dead.
     */
    private static final class SpaFallbackResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String path, Resource location) throws IOException {
            Resource requested = location.createRelative(path);
            if (requested.exists() && requested.isReadable()) {
                return requested;
            }

            // A wrong URL under /api must 404 like the API it is. Handing back the SPA shell would mean
            // a typo'd endpoint answers 200 with a page of HTML, and every client bug arrives disguised
            // as a rendering bug.
            if (path.startsWith("api/") || path.startsWith("actuator")) {
                return null;
            }

            Resource index = new ClassPathResource(INDEX);
            // No bundle on the classpath at all: local dev, where Vite is serving the SPA. Nothing to
            // fall back to, so answer 404 rather than 500.
            return index.exists() ? index : null;
        }
    }
}
