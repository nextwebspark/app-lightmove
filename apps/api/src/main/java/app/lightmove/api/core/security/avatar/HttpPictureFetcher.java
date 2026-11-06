package app.lightmove.api.core.security.avatar;

import app.lightmove.api.core.http.model.GuardedFetchFailure;
import app.lightmove.api.core.http.model.GuardedResponse;
import app.lightmove.api.core.http.service.GuardedHttpClient;
import app.lightmove.api.core.http.service.PublicAddressResolver;
import java.io.IOException;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.DnsResolver;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Fetches the picture a provider's {@code picture} claim names — often user-editable, so through {@link GuardedHttpClient}. */
@Slf4j
@Component
public class HttpPictureFetcher implements PictureFetcher, DisposableBean {

    static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final Duration STEP_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEADLINE = Duration.ofSeconds(10);

    private final GuardedHttpClient http;

    @Autowired
    public HttpPictureFetcher() throws NoSuchAlgorithmException {
        this(new PublicAddressResolver(), SSLContext.getDefault(), STEP_TIMEOUT, DEADLINE);
    }

    HttpPictureFetcher(DnsResolver resolver, SSLContext tls, Duration stepTimeout, Duration deadline) {
        this.http = new GuardedHttpClient("avatar-fetch", resolver, tls, stepTimeout, deadline);
    }

    @Override
    public PictureFetch fetch(URI pictureUrl) {
        if (!"https".equalsIgnoreCase(pictureUrl.getScheme()) || pictureUrl.getHost() == null) {
            return new PictureFetch.Gone("not an https URL");
        }
        try {
            // A provider may answer a format the JVM cannot decode (WebP) when the client says it takes one.
            return http.get(pictureUrl, "image/jpeg, image/png", MAX_BYTES, HttpPictureFetcher::read);
        } catch (GuardedFetchFailure failed) {
            return switch (failed.kind()) {
                case PRIVATE_ADDRESS -> new PictureFetch.Gone("not a public address");
                case TOO_LARGE -> new PictureFetch.Gone("too large");
                case TIMEOUT, UNREACHABLE -> {
                    log.info("Could not fetch a profile picture: {}", failed.kind());
                    yield new PictureFetch.Unavailable(failed.kind().name());
                }
            };
        }
    }

    private static PictureFetch read(GuardedResponse response) throws IOException {
        int status = response.status();
        if (status >= 500 || status == 408 || status == 429) {
            return new PictureFetch.Unavailable("HTTP " + status);
        }
        if (status != 200) {
            return new PictureFetch.Gone("HTTP " + status);
        }
        byte[] content = response.body();
        return content.length == 0 ? new PictureFetch.Gone("empty") : new PictureFetch.Fetched(content);
    }

    @Override
    public void destroy() throws IOException {
        http.close();
    }
}
