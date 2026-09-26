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
 * Fetches a provider's photo URL into bytes, or a logged null — losing a photo never fails an
 * enrichment. The URL is a third party's (SSRF): https only, no private or loopback host, no
 * redirects, raster types only (an SVG is script served from our origin), and a bounded read. Its own
 * bare {@link RestClient} so a CDN never sees vendor credentials.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfilePhotoDownloader {

    private static final Set<String> RASTER_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final int MAX_DOWNLOAD_BYTES = 2 * 1024 * 1024;
    private static final RestClient CLIENT = bareClient();

    private final ProfilePhotoTransfer transfer;

    public EnrichedPhoto fetchOrNull(String photoUrl) {
        URI address = publicHttpsUri(photoUrl);
        if (address == null) {
            return null;
        }
        try {
            EnrichedPhoto downloaded = transfer.download(address);
            return downloaded == null ? null : ProfilePhotoThumbnail.shrink(downloaded);
        } catch (RuntimeException failed) {
            // The host, never the URL: the path names the person the photo is of.
            log.info("Profile photo fetch failed for {}: {}", address.getHost(), failed.getMessage());
            return null;
        }
    }

    /**
     * Its own bean because {@code @Retryable} is proxy-based and cannot see an exception that
     * {@link ProfilePhotoDownloader#fetchOrNull} swallows.
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
                if (type == null || !RASTER_TYPES.contains(type) || declared > MAX_DOWNLOAD_BYTES) {
                    log.info("Skipping profile photo ({}, {} declared bytes)", type, declared);
                    return null;
                }
                byte[] content = readBounded(response.getBody());
                return content == null ? null : new EnrichedPhoto(content, type);
            });
        }
    }

    private static byte[] readBounded(InputStream body) throws IOException {
        byte[] content = body.readNBytes(MAX_DOWNLOAD_BYTES);
        if (content.length == 0 || body.read() != -1) {
            log.info("Skipping profile photo (empty, or larger than the {}KB cap)",
                    MAX_DOWNLOAD_BYTES / 1024);
            return null;
        }
        return content;
    }

    /** Null for anything but a public https URL: a vendor record may name the metadata service. */
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
                // The host, not the URL: a CDN hostname names no one, the path would.
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
                // A redirect would step around the https-and-public check.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder().requestFactory(factory).build();
    }
}
