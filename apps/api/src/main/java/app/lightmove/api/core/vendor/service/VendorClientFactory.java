package app.lightmove.api.core.vendor.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.VendorSettings;
import app.lightmove.api.core.logging.service.CorrelationId;
import app.lightmove.api.core.vendor.constant.VendorFailureKind;
import app.lightmove.api.core.vendor.model.VendorClientSpec;
import app.lightmove.api.core.vendor.model.VendorFailure;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Builds the one kind of HTTP client this application is allowed to point at a paid API.
 *
 * <p>Everything here is a thing an adapter would otherwise have to remember, and eventually not:
 * timeouts on both ends, the key in a header, the correlation id carried across, and — the reason the
 * factory exists at all — every non-2xx already classified before the adapter sees it. An adapter
 * built through this cannot accidentally ship without a read timeout or with a bare
 * {@code HttpClientErrorException} escaping into a service.
 *
 * <p><b>The builder is a parameter, not a field.</b> {@code RestClient.Builder} mutates in place, so
 * a factory holding one would carry the first vendor's base URL and API key into the second's client.
 * Each caller brings its own.
 *
 * <p><b>The transport is always ours, and that is deliberate even though it costs something.</b>
 * Setting the request factory here is what makes a vendor client without timeouts impossible to
 * write — but it also overwrites whatever the builder arrived with, which rules out
 * {@code MockRestServiceServer}: it works by installing its own request factory, and this would
 * replace it and send the test at the real internet. Tests therefore use a real loopback server
 * ({@code StubVendorServer}). That is the right way round: a guarantee that holds in production is
 * worth more than a convenience in tests, and the loopback server exercises this class's status
 * handling and header parsing rather than stubbing past them.
 */
@Component
public class VendorClientFactory {

    /** Enough of a failed body to recognise it in a test; never enough to be worth logging. */
    private static final int ERROR_BODY_SNIPPET_LIMIT = 512;

    private final VendorSettings settings;

    public VendorClientFactory(LightMoveProperties properties) {
        this.settings = properties.vendor();
    }

    public RestClient create(VendorClientSpec spec, RestClient.Builder builder) {
        requireUsable(spec);

        return builder
                .requestFactory(timeoutBoundFactory(spec))
                .baseUrl(spec.baseUrl())
                .defaultHeader(spec.apiKeyHeader(), spec.authHeaderValue())
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().set(CorrelationId.HEADER, CorrelationId.current());
                    return execution.execute(request, body);
                })
                // Registered across every error status rather than an enumerated few. Anything this
                // predicate misses falls through to Spring's own handler, which puts the response
                // body verbatim into the exception message — and a vendor's error body echoes the
                // query, so that is a person's name in a log. The breadth is a privacy control.
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    throw new VendorResponseFailure(classify(response, spec));
                })
                .build();
    }

    private static VendorFailure classify(ClientHttpResponse response, VendorClientSpec spec) throws IOException {
        HttpStatusCode status = response.getStatusCode();
        return new VendorFailure(
                VendorFailureKind.of(status),
                status.value(),
                retryAfter(response.getHeaders()),
                spec.captureErrorBody() ? bodySnippet(response) : null);
    }

    /** Both forms RFC 9110 allows: delay-seconds, or an HTTP date. Neither is trusted to parse. */
    private static Duration retryAfter(HttpHeaders headers) {
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(value.trim()));
        } catch (NumberFormatException notSeconds) {
            try {
                long millis = headers.getFirstDate(HttpHeaders.RETRY_AFTER) - System.currentTimeMillis();
                return millis > 0 ? Duration.ofMillis(millis) : Duration.ZERO;
            } catch (IllegalArgumentException | DateTimeParseException notADate) {
                return null;
            }
        }
    }

    /**
     * Read defensively. An {@code IOException} escaping a status handler is converted to a
     * {@code RestClientException} by {@code RestClient}, which would lose the classification we came
     * here to make — a truncated error body would turn a clean 402 into an unclassified failure.
     */
    private static String bodySnippet(ClientHttpResponse response) {
        try (var body = response.getBody()) {
            byte[] bytes = body.readNBytes(ERROR_BODY_SNIPPET_LIMIT);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException unreadable) {
            return null;
        }
    }

    /**
     * Fail at startup rather than at the first call. A deployment that boots happily and then throws
     * on every sourcing run is worse than one that refuses to boot — the same bargain
     * {@code EmailSenderConfig} makes about a missing mail key.
     */
    private static void requireUsable(VendorClientSpec spec) {
        if (spec.apiKey() == null || spec.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "Vendor '%s' is enabled but has no API key configured".formatted(spec.vendor()));
        }
        if (spec.baseUrl() == null || spec.baseUrl().contains("?")) {
            // A key in a query string ends up in access logs, proxy logs and exception messages,
            // none of which redact. Refusing the shape is cheaper than auditing for it later.
            throw new IllegalStateException(
                    "Vendor '%s' base URL must be a scheme and host with no query string".formatted(spec.vendor()));
        }
    }

    private JdkClientHttpRequestFactory timeoutBoundFactory(VendorClientSpec spec) {
        Duration connectTimeout = spec.connectTimeout() != null ? spec.connectTimeout() : settings.connectTimeout();
        Duration readTimeout = spec.readTimeout() != null ? spec.readTimeout() : settings.readTimeout();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(connectTimeout).build());
        factory.setReadTimeout(readTimeout);
        return factory;
    }
}
