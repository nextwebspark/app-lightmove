package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.model.EnrichedPhoto;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Fetches a provider's photo URL into bytes, or nothing. Losing a photo never fails an enrichment, so
 * every path out of here is a value or a logged null.
 *
 * <p><b>The URL is a third party's, so it is treated as one.</b> It arrives inside a vendor payload
 * describing a page we did not author, which makes this a server-side request to an address someone
 * else chose: https only, no private or loopback address, and the body is refused on its declared
 * length and then read through a bounded stream, so an oversized image costs a buffer rather than
 * however many megabytes it actually is. Raster formats only — an SVG is a script the workspace would
 * later be served from our own origin.
 *
 * <p>Deliberately its own bare {@link RestClient}: the adapters' clients carry vendor credentials as
 * default headers, and a CDN must never see those keys. It bypasses the vendor layer for the same
 * reason — nothing here is metered, so there is no rate to pace and no failure worth classifying.
 * Only a transport failure is retried; a CDN's 404 means the photo is gone.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfilePhotoDownloader {

    private static final Set<String> RASTER_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final int MAX_BYTES = 512 * 1024;
    private static final RestClient CLIENT = bareClient();

    private final ProfilePhotoTransfer transfer;

    public EnrichedPhoto fetchOrNull(String photoUrl) {
        URI address = publicHttpsUri(photoUrl);
        if (address == null) {
            return null;
        }
        try {
            return transfer.download(address);
        } catch (RuntimeException ex) {
            log.debug("Profile photo fetch failed for {}: {}", photoUrl, ex.getMessage());
            return null;
        }
    }

    /**
     * The transfer itself, on its own bean so the retry is not swallowed before it happens.
     * {@code @Retryable} is proxy-based, so a transport failure has to escape the retried method to be
     * seen — and {@link ProfilePhotoDownloader#fetchOrNull} exists precisely to let nothing escape.
     * Calling it on {@code this} would bypass the proxy, the same trap {@code AuditService} documents
     * for {@code @Async}.
     */
    @Component
    static class ProfilePhotoTransfer {

        @Retryable(includes = ResourceAccessException.class, maxRetries = 2, delay = 300)
        EnrichedPhoto download(URI address) {
            return CLIENT.get().uri(address).exchange((request, response) -> {
                MediaType contentType = response.getHeaders().getContentType();
                String type = contentType == null ? null
                        : contentType.getType() + "/" + contentType.getSubtype();
                long declared = response.getHeaders().getContentLength();
                if (type == null || !RASTER_TYPES.contains(type) || declared > MAX_BYTES) {
                    log.info("Skipping profile photo ({}, {} declared bytes)", type, declared);
                    return null;
                }
                byte[] content = readBounded(response.getBody());
                return content == null ? null : new EnrichedPhoto(content, type);
            });
        }
    }

    /** Reads at most the cap; a body that keeps going past it is refused rather than buffered whole. */
    private static byte[] readBounded(InputStream body) throws IOException {
        byte[] content = body.readNBytes(MAX_BYTES);
        if (content.length == 0 || body.read() != -1) {
            log.info("Skipping profile photo (empty, or larger than the {}KB cap)", MAX_BYTES / 1024);
            return null;
        }
        return content;
    }

    /**
     * The URL as something safe to fetch, or null. A vendor record naming {@code http://10.0.0.1/} or
     * the metadata service must not turn this server into its errand runner, and the type check above
     * gates only what we would store — not the request we would already have made.
     */
    private static URI publicHttpsUri(String photoUrl) {
        if (photoUrl == null || photoUrl.isBlank()) {
            return null;
        }
        URI parsed;
        try {
            parsed = URI.create(photoUrl.trim());
        } catch (IllegalArgumentException notAUri) {
            return null;
        }
        if (parsed.getScheme() == null
                || !parsed.getScheme().toLowerCase(Locale.ROOT).equals("https")
                || parsed.getHost() == null) {
            log.debug("Refusing a profile photo that is not an https URL: {}", photoUrl);
            return null;
        }
        try {
            InetAddress resolved = InetAddress.getByName(parsed.getHost());
            if (resolved.isAnyLocalAddress() || resolved.isLoopbackAddress()
                    || resolved.isLinkLocalAddress() || resolved.isSiteLocalAddress()) {
                // The host, not the URL: this one stays at warn because it is a security event worth
                // seeing, and a CDN hostname names no one — the path is the part that would.
                log.warn("Refusing a profile photo whose host resolves inside the network: {}",
                        parsed.getHost());
                return null;
            }
        } catch (UnknownHostException unresolvable) {
            return null;
        }
        return parsed;
    }

    private static RestClient bareClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                // NEVER: a redirect is a second address the vendor chose, and following it would step
                // around the https-and-public check made above.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder().requestFactory(factory).build();
    }
}
