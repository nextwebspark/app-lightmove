package app.lightmove.api.core.security.avatar;

import app.lightmove.api.core.security.oauth.PublicAddressResolver;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Fetches the picture a provider's {@code picture} claim names — a URL that is user-editable on many providers, so it
 * is treated as a stranger's: https and a public address only (every address the host resolves to, and the connection
 * dials exactly those), no redirect, {@link #MAX_BYTES}, and one deadline for the whole exchange.
 */
@Slf4j
@Component
public class HttpPictureFetcher implements PictureFetcher, DisposableBean {

    static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final Duration STEP_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEADLINE = Duration.ofSeconds(10);

    private final CloseableHttpClient http;
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "avatar-fetch-deadline");
        thread.setDaemon(true);
        return thread;
    });
    private final Duration deadline;

    @Autowired
    public HttpPictureFetcher() throws NoSuchAlgorithmException {
        this(new PublicAddressResolver(), SSLContext.getDefault(), STEP_TIMEOUT, DEADLINE);
    }

    HttpPictureFetcher(DnsResolver resolver, SSLContext tls, Duration stepTimeout, Duration deadline) {
        this.deadline = deadline;
        Timeout perStep = Timeout.of(stepTimeout);
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
    public PictureFetch fetch(URI pictureUrl) {
        if (!"https".equalsIgnoreCase(pictureUrl.getScheme()) || pictureUrl.getHost() == null) {
            return new PictureFetch.Gone("not an https URL");
        }
        HttpGet request = new HttpGet(pictureUrl);
        // A provider may answer a format the JVM cannot decode (WebP) when the client says it takes one.
        request.setHeader(HttpHeaders.ACCEPT, "image/jpeg, image/png");
        AtomicBoolean timedOut = new AtomicBoolean();
        ScheduledFuture<?> stop = deadlines.schedule(() -> {
            timedOut.set(true);
            request.cancel();
        }, deadline.toMillis(), TimeUnit.MILLISECONDS);
        try {
            return http.execute(request, response -> {
                PictureFetch answer = read(response);
                if (!(answer instanceof PictureFetch.Fetched)) {
                    // Aborted, never drained: closing would read an endless body to reuse the connection.
                    request.cancel();
                }
                return answer;
            });
        } catch (IOException | RuntimeException failed) {
            return timedOut.get() ? new PictureFetch.Unavailable("timed out") : translate(failed);
        } finally {
            stop.cancel(false);
        }
    }

    private static PictureFetch read(ClassicHttpResponse response) throws IOException {
        int status = response.getCode();
        if (status >= 500 || status == 408 || status == 429) {
            return new PictureFetch.Unavailable("HTTP " + status);
        }
        if (status != 200) {
            return new PictureFetch.Gone("HTTP " + status);
        }
        HttpEntity entity = response.getEntity();
        if (entity == null || entity.getContentLength() > MAX_BYTES) {
            return new PictureFetch.Gone(entity == null ? "no body" : "too large");
        }
        byte[] content = readAtMost(entity.getContent());
        if (content == null) {
            return new PictureFetch.Gone("too large");
        }
        return content.length == 0 ? new PictureFetch.Gone("empty") : new PictureFetch.Fetched(content);
    }

    /** Null past the cap. */
    private static byte[] readAtMost(InputStream in) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            body.write(buffer, 0, read);
            if (body.size() > MAX_BYTES) {
                return null;
            }
        }
        in.close();
        return body.toByteArray();
    }

    private static PictureFetch translate(Exception failed) {
        for (Throwable cause = failed; cause != null; cause = cause.getCause()) {
            if (cause instanceof PublicAddressResolver.NonPublicAddressException) {
                return new PictureFetch.Gone("not a public address");
            }
        }
        log.info("Could not fetch a profile picture: {}", failed.getClass().getSimpleName());
        return new PictureFetch.Unavailable(failed.getClass().getSimpleName());
    }

    @Override
    public void destroy() throws IOException {
        deadlines.shutdownNow();
        http.close();
    }
}
