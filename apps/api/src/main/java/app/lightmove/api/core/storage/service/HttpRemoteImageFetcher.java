package app.lightmove.api.core.storage.service;

import app.lightmove.api.core.security.oauth.PublicAddressResolver;
import app.lightmove.api.core.storage.model.RemoteImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/**
 * The address is a third party's (SSRF): https only, every address the host resolves to public — and the connection
 * dials exactly those — no redirect, raster types only (an SVG is script), 2 MB, and one deadline for the whole exchange.
 */
@Slf4j
@Component
public class HttpRemoteImageFetcher implements RemoteImageFetcher, DisposableBean {

    private static final Set<String> RASTER_TYPES = Set.of("image/png", "image/jpeg");
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final Duration DEADLINE = Duration.ofSeconds(5);

    private final CloseableHttpClient http;
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "remote-image-deadline");
        thread.setDaemon(true);
        return thread;
    });

    public HttpRemoteImageFetcher() throws NoSuchAlgorithmException {
        Timeout perStep = Timeout.of(DEADLINE);
        this.http = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(new PublicAddressResolver())
                        .setTlsSocketStrategy(new DefaultClientTlsStrategy(SSLContext.getDefault()))
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
                .build();
    }

    @Override
    public Optional<RemoteImage> fetch(URI address) {
        if (address == null || !"https".equalsIgnoreCase(address.getScheme()) || address.getHost() == null) {
            return Optional.empty();
        }
        HttpGet request = new HttpGet(address);
        ScheduledFuture<?> deadline = deadlines.schedule(request::cancel, DEADLINE.toMillis(), TimeUnit.MILLISECONDS);
        try {
            return http.execute(request, response -> {
                HttpEntity entity = response.getEntity();
                String type = entity == null || entity.getContentType() == null ? ""
                        : entity.getContentType().split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
                if (response.getCode() != HttpStatus.SC_OK || !RASTER_TYPES.contains(type)
                        || entity.getContentLength() > MAX_BYTES) {
                    request.cancel();
                    log.info("Not copying a picture from {}: HTTP {}, {}", address.getHost(), response.getCode(), type);
                    return Optional.<RemoteImage>empty();
                }
                byte[] content = readAtMost(entity.getContent());
                if (content == null) {
                    request.cancel();
                    log.info("Not copying a picture from {}: larger than {} bytes", address.getHost(), MAX_BYTES);
                    return Optional.<RemoteImage>empty();
                }
                return Optional.of(new RemoteImage(content, type));
            });
        } catch (IOException | RuntimeException failed) {
            // The host, never the address: a path may name whoever the picture is of.
            log.info("Copying a picture from {} failed: {}", address.getHost(), failed.getMessage());
            return Optional.empty();
        } finally {
            deadline.cancel(false);
        }
    }

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
        return body.toByteArray();
    }

    @Override
    public void destroy() throws IOException {
        deadlines.shutdownNow();
        http.close();
    }
}
