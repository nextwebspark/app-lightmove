package app.lightmove.api.core.security.oauth;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.McpSettings;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Fetches a client id metadata document from a URL a stranger chose: https and a public address only, no redirect, a
 * few kilobytes, and one deadline for the whole exchange, so a host trickling a byte at a time is cut off there.
 */
@Slf4j
@Component
public class HttpClientMetadataFetcher implements ClientMetadataFetcher, DisposableBean {

    private static final Pattern MAX_AGE = Pattern.compile("max-age\\s*=\\s*\"?(\\d+)\"?");

    private final CloseableHttpClient http;
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "client-metadata-deadline");
        thread.setDaemon(true);
        return thread;
    });
    private final Duration timeout;
    private final int maxBytes;
    private final Duration minTtl;
    private final Duration maxTtl;

    @Autowired
    public HttpClientMetadataFetcher(LightMoveProperties properties) throws NoSuchAlgorithmException {
        this(properties.mcp(), new PublicAddressResolver(), SSLContext.getDefault());
    }

    HttpClientMetadataFetcher(McpSettings settings, DnsResolver resolver, SSLContext tls) {
        this.timeout = settings.cimdTimeout();
        this.maxBytes = settings.cimdMaxBytes();
        this.minTtl = settings.cimdMinTtl();
        this.maxTtl = settings.cimdMaxTtl();
        Timeout perStep = Timeout.of(timeout);
        this.http = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(resolver)
                        .setTlsSocketStrategy(new DefaultClientTlsStrategy(tls))
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(perStep)
                                .setSocketTimeout(perStep)
                                .build())
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(perStep)
                        .setResponseTimeout(perStep)
                        .setRedirectsEnabled(false)
                        .build())
                .disableRedirectHandling()
                .disableCookieManagement()
                .disableAutomaticRetries()
                .disableContentCompression()
                .build();
    }

    @Override
    public FetchedClientMetadata fetch(URI documentUrl) {
        if (!"https".equalsIgnoreCase(documentUrl.getScheme())) {
            throw new ClientMetadataUnavailable(ClientMetadataRefusal.NOT_HTTPS, documentUrl.toString());
        }
        HttpGet request = new HttpGet(documentUrl);
        request.setHeader(HttpHeaders.ACCEPT, "application/json");
        AtomicBoolean timedOut = new AtomicBoolean();
        ScheduledFuture<?> deadline = deadlines.schedule(() -> {
            timedOut.set(true);
            request.cancel();
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);
        try {
            return http.execute(request, response -> {
                try {
                    return read(response);
                } catch (ClientMetadataUnavailable refused) {
                    // Aborted, never closed: closing would read the rest of the body to reuse the connection, and an
                    // endless one would then run to the deadline and pass for an outage.
                    request.cancel();
                    throw refused;
                }
            });
        } catch (ClientMetadataUnavailable refused) {
            throw timedOut.get() ? new ClientMetadataUnavailable(ClientMetadataRefusal.TIMEOUT, documentUrl.toString())
                    : refused;
        } catch (IOException | RuntimeException failed) {
            throw timedOut.get() ? new ClientMetadataUnavailable(ClientMetadataRefusal.TIMEOUT, documentUrl.toString())
                    : translate(documentUrl, failed);
        } finally {
            deadline.cancel(false);
        }
    }

    private FetchedClientMetadata read(ClassicHttpResponse response) throws IOException {
        int status = response.getCode();
        if (status >= HttpStatus.SC_REDIRECTION && status < HttpStatus.SC_CLIENT_ERROR) {
            throw new ClientMetadataUnavailable(ClientMetadataRefusal.REDIRECT, "HTTP " + status);
        }
        if (status != HttpStatus.SC_OK) {
            throw new ClientMetadataUnavailable(ClientMetadataRefusal.HTTP_ERROR, "HTTP " + status);
        }
        HttpEntity entity = response.getEntity();
        if (entity == null || !isJson(entity.getContentType())) {
            throw new ClientMetadataUnavailable(ClientMetadataRefusal.NOT_JSON,
                    entity == null ? "no body" : String.valueOf(entity.getContentType()));
        }
        if (entity.getContentLength() > maxBytes) {
            throw new ClientMetadataUnavailable(ClientMetadataRefusal.TOO_LARGE, entity.getContentLength() + " bytes");
        }
        Header cacheControl = response.getFirstHeader(HttpHeaders.CACHE_CONTROL);
        return new FetchedClientMetadata(readAtMost(entity.getContent()),
                lifetimeOf(cacheControl == null ? null : cacheControl.getValue()));
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

    /** Not closed here on an overflow: {@link #fetch} aborts the exchange instead. */
    private String readAtMost(InputStream in) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            body.write(buffer, 0, read);
            if (body.size() > maxBytes) {
                throw new ClientMetadataUnavailable(ClientMetadataRefusal.TOO_LARGE, "over " + maxBytes + " bytes");
            }
        }
        in.close();
        return body.toString(StandardCharsets.UTF_8);
    }

    private static boolean isJson(String contentType) {
        if (contentType == null) {
            return false;
        }
        String mediaType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return mediaType.equals("application/json") || mediaType.endsWith("+json");
    }

    private ClientMetadataUnavailable translate(URI documentUrl, Exception failed) {
        for (Throwable cause = failed; cause != null; cause = cause.getCause()) {
            if (cause instanceof PublicAddressResolver.NonPublicAddressException) {
                return new ClientMetadataUnavailable(ClientMetadataRefusal.PRIVATE_ADDRESS, documentUrl.getHost());
            }
            if (cause instanceof ClientMetadataUnavailable refused) {
                return refused;
            }
        }
        if (failed instanceof InterruptedIOException) {
            return new ClientMetadataUnavailable(ClientMetadataRefusal.TIMEOUT, documentUrl.toString(), failed);
        }
        return new ClientMetadataUnavailable(ClientMetadataRefusal.UNREACHABLE, documentUrl.toString(), failed);
    }

    @Override
    public void destroy() throws IOException {
        deadlines.shutdownNow();
        http.close();
    }
}
