package app.lightmove.api.core.security.oauth;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.McpSettings;
import app.lightmove.api.core.http.model.GuardedFetchFailure;
import app.lightmove.api.core.http.model.GuardedResponse;
import app.lightmove.api.core.http.service.GuardedHttpClient;
import app.lightmove.api.core.http.service.PublicAddressResolver;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Fetches a client id metadata document from a URL a stranger chose, through {@link GuardedHttpClient}. */
@Component
public class HttpClientMetadataFetcher implements ClientMetadataFetcher, DisposableBean {

    private static final Pattern MAX_AGE = Pattern.compile("max-age\\s*=\\s*\"?(\\d+)\"?");

    private final GuardedHttpClient http;
    private final int maxBytes;
    private final Duration minTtl;
    private final Duration maxTtl;

    @Autowired
    public HttpClientMetadataFetcher(LightMoveProperties properties) throws NoSuchAlgorithmException {
        this(properties.mcp(), new PublicAddressResolver(), SSLContext.getDefault());
    }

    HttpClientMetadataFetcher(McpSettings settings, DnsResolver resolver, SSLContext tls) {
        this.maxBytes = settings.cimdMaxBytes();
        this.minTtl = settings.cimdMinTtl();
        this.maxTtl = settings.cimdMaxTtl();
        this.http = new GuardedHttpClient("client-metadata", resolver, tls, settings.cimdTimeout(),
                settings.cimdTimeout());
    }

    @Override
    public FetchedClientMetadata fetch(URI documentUrl) {
        if (!"https".equalsIgnoreCase(documentUrl.getScheme())) {
            throw new ClientMetadataUnavailable(ClientMetadataRefusal.NOT_HTTPS, documentUrl.toString());
        }
        try {
            return http.get(documentUrl, "application/json", maxBytes, this::read);
        } catch (GuardedFetchFailure failed) {
            throw new ClientMetadataUnavailable(refusalOf(failed), failed.getMessage(), failed.getCause());
        }
    }

    private FetchedClientMetadata read(GuardedResponse response) throws IOException {
        int status = response.status();
        if (status >= HttpStatus.SC_REDIRECTION && status < HttpStatus.SC_CLIENT_ERROR) {
            throw new ClientMetadataUnavailable(ClientMetadataRefusal.REDIRECT, "HTTP " + status);
        }
        if (status != HttpStatus.SC_OK) {
            throw new ClientMetadataUnavailable(ClientMetadataRefusal.HTTP_ERROR, "HTTP " + status);
        }
        if (!isJson(response.contentType())) {
            throw new ClientMetadataUnavailable(ClientMetadataRefusal.NOT_JSON, String.valueOf(response.contentType()));
        }
        String body = new String(response.body(), StandardCharsets.UTF_8);
        return new FetchedClientMetadata(body, lifetimeOf(response.header(HttpHeaders.CACHE_CONTROL)));
    }

    /** {@code no-store} or {@code no-cache} keeps it the least while, as does a host that says nothing. */
    Duration lifetimeOf(String cacheControl) {
        if (cacheControl == null) {
            return minTtl;
        }
        String directives = cacheControl.toLowerCase(Locale.ROOT);
        if (directives.contains("no-store") || directives.contains("no-cache")) {
            return minTtl;
        }
        Matcher maxAge = MAX_AGE.matcher(directives);
        if (!maxAge.find()) {
            return minTtl;
        }
        Duration stated;
        try {
            stated = Duration.ofSeconds(Long.parseLong(maxAge.group(1)));
        } catch (NumberFormatException tooLong) {
            return maxTtl;
        }
        return stated.compareTo(minTtl) < 0 ? minTtl : stated.compareTo(maxTtl) > 0 ? maxTtl : stated;
    }

    private static boolean isJson(String contentType) {
        if (contentType == null) {
            return false;
        }
        String mediaType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return mediaType.equals("application/json") || mediaType.endsWith("+json");
    }

    private static ClientMetadataRefusal refusalOf(GuardedFetchFailure failed) {
        return switch (failed.kind()) {
            case PRIVATE_ADDRESS -> ClientMetadataRefusal.PRIVATE_ADDRESS;
            case TIMEOUT -> ClientMetadataRefusal.TIMEOUT;
            case TOO_LARGE -> ClientMetadataRefusal.TOO_LARGE;
            case UNREACHABLE -> ClientMetadataRefusal.UNREACHABLE;
        };
    }

    @Override
    public void destroy() throws IOException {
        http.close();
    }
}
