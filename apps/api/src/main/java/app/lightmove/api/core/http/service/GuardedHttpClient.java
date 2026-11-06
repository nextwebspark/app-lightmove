package app.lightmove.api.core.http.service;

import app.lightmove.api.core.http.constant.GuardedFetchFailureKind;
import app.lightmove.api.core.http.model.GuardedFetchFailure;
import app.lightmove.api.core.http.model.GuardedResponse;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.util.Timeout;

/**
 * A GET of a URL a stranger chose, from inside our network: every address the host resolves to must be public and the
 * connection dials exactly those ({@link PublicAddressResolver}), no redirect is followed, the body is capped, and one
 * deadline covers the whole exchange, so a host trickling a byte at a time is cut off there.
 */
public final class GuardedHttpClient implements AutoCloseable {

    private final CloseableHttpClient http;
    private final ScheduledExecutorService deadlines;
    private final Duration deadline;

    public GuardedHttpClient(String name, DnsResolver resolver, SSLContext tls, Duration stepTimeout,
                             Duration deadline) {
        this.deadline = deadline;
        this.deadlines = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, name + "-deadline");
            thread.setDaemon(true);
            return thread;
        });
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

    /** Guarded by {@link PublicAddressResolver} over the JVM's own trust store. */
    public static GuardedHttpClient publicOnly(String name, Duration stepTimeout, Duration deadline) {
        try {
            return new GuardedHttpClient(name, new PublicAddressResolver(), SSLContext.getDefault(), stepTimeout,
                    deadline);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("No default TLS context", impossible);
        }
    }

    /**
     * Hands the answer to {@code reader}; past the deadline, whatever the reader was doing, it is
     * {@link GuardedFetchFailureKind#TIMEOUT}. A body the reader left unread is never drained: it could be endless.
     *
     * @throws GuardedFetchFailure when there was no answer to read, or the body ran past {@code maxBytes}
     */
    public <T> T get(URI url, String accept, int maxBytes, GuardedResponseReader<T> reader) {
        HttpGet request = new HttpGet(url);
        request.setHeader(HttpHeaders.ACCEPT, accept);
        AtomicBoolean timedOut = new AtomicBoolean();
        AtomicReference<T> answerWithoutBody = new AtomicReference<>();
        ScheduledFuture<?> stop = deadlines.schedule(() -> {
            timedOut.set(true);
            request.cancel();
        }, deadline.toMillis(), TimeUnit.MILLISECONDS);
        try {
            return http.execute(request, response -> {
                GuardedResponse guarded = new GuardedResponse(response, maxBytes);
                T answer;
                try {
                    answer = reader.read(guarded);
                } catch (GuardedFetchFailure | IOException failed) {
                    request.cancel();
                    throw failed;
                } catch (RuntimeException refused) {
                    request.cancel();
                    throw new ReaderRefusal(refused);
                }
                if (guarded.isBodyRead()) {
                    return answer;
                }
                // Returned normally, the client would read the rest of the body to reuse the connection.
                answerWithoutBody.set(answer);
                request.cancel();
                throw new AnsweredWithoutBody();
            });
        } catch (AnsweredWithoutBody answered) {
            return answerWithoutBody.get();
        } catch (ReaderRefusal refusal) {
            if (timedOut.get()) {
                throw timeout(url);
            }
            throw refusal.refusal;
        } catch (GuardedFetchFailure failure) {
            throw timedOut.get() ? timeout(url) : failure;
        } catch (IOException | RuntimeException failed) {
            throw timedOut.get() ? timeout(url) : translate(url, failed);
        } finally {
            stop.cancel(false);
        }
    }

    private static GuardedFetchFailure timeout(URI url) {
        return new GuardedFetchFailure(GuardedFetchFailureKind.TIMEOUT, url.toString(), null);
    }

    private static GuardedFetchFailure translate(URI url, Exception failed) {
        for (Throwable cause = failed; cause != null; cause = cause.getCause()) {
            if (cause instanceof PublicAddressResolver.NonPublicAddressException) {
                return new GuardedFetchFailure(GuardedFetchFailureKind.PRIVATE_ADDRESS, url.getHost(), null);
            }
            if (cause instanceof GuardedFetchFailure failure) {
                return failure;
            }
        }
        GuardedFetchFailureKind kind = failed instanceof InterruptedIOException
                ? GuardedFetchFailureKind.TIMEOUT
                : GuardedFetchFailureKind.UNREACHABLE;
        return new GuardedFetchFailure(kind, url.toString(), failed);
    }

    @Override
    public void close() throws IOException {
        deadlines.shutdownNow();
        http.close();
    }

    /** Carries a reader's own refusal past the client, which would otherwise read it as a transport failure. */
    private static final class ReaderRefusal extends RuntimeException {

        private final RuntimeException refusal;

        ReaderRefusal(RuntimeException refusal) {
            super(null, null, false, false);
            this.refusal = refusal;
        }
    }

    private static final class AnsweredWithoutBody extends RuntimeException {

        AnsweredWithoutBody() {
            super(null, null, false, false);
        }
    }
}
