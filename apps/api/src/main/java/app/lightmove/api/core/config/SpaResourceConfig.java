package app.lightmove.api.core.config;

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
 * Serves the built SPA from the API's own origin, which the {@code SameSite=Strict}, host-only refresh
 * cookie requires. The bundle is absent in local development, so everything must cope with nothing to serve.
 */
@Configuration
public class SpaResourceConfig implements WebMvcConfigurer {

    private static final String STATIC_ROOT = "classpath:/static/";
    private static final String INDEX = "/static/index.html";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Content-hashed by Vite, so a URL's bytes never change.
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(STATIC_ROOT + "assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());

        // A cached index.html would ask for asset hashes that no longer exist.
        registry.addResourceHandler("/**")
                .addResourceLocations(STATIC_ROOT)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new SpaFallbackResolver());
    }

    /** The requested file, or the SPA shell for a client-side route such as {@code /auth/verify?token=…}. */
    private static final class SpaFallbackResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String path, Resource location) throws IOException {
            Resource requested = location.createRelative(path);
            if (requested.exists() && requested.isReadable()) {
                return requested;
            }

            // A typo'd endpoint must 404, not answer 200 with the SPA shell.
            if (path.startsWith("api/") || path.startsWith("actuator")) {
                return null;
            }

            Resource index = new ClassPathResource(INDEX);
            // Local dev has no bundle: 404 rather than 500.
            return index.exists() ? index : null;
        }
    }
}
