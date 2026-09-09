package app.lightmove.api.core.resilience.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.ResilienceSettings;
import app.lightmove.api.core.logging.service.CorrelationId;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorClientSpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

/**
 * Builds the one kind of HTTP client this application points at a paid API: timeouts on both ends,
 * the key in a header (or, for a vendor that insists, a query parameter), the correlation id carried
 * across, and every non-2xx already classified before the adapter sees it. A client built here cannot ship without a read timeout, and cannot leak a bare
 * {@code HttpClientErrorException} into a service.
 *
 * <p>The status handler is registered across <i>every</i> error status rather than an enumerated few,
 * and the breadth is a privacy control: whatever it misses falls through to Spring's own handler,
 * which puts the response body verbatim into the exception message — and a vendor's error body echoes
 * the query, so that is a researched person's name in a log.
 *
 * <p>The builder is a parameter rather than a field because {@code RestClient.Builder} mutates in
 * place: a factory holding one would carry the first vendor's base URL and key into the second's
 * client.
 */
@Component
public class VendorClientFactory {

    private final ResilienceSettings settings;

    public VendorClientFactory(LightMoveProperties properties) {
        this.settings = properties.resilience();
    }

    public RestClient create(VendorClientSpec spec, RestClient.Builder builder,
                             VendorRateLimiter rateLimiter) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(settings.connectTimeout()).build());
        requestFactory.setReadTimeout(spec.readTimeout());
        return configure(spec, builder.requestFactory(requestFactory), rateLimiter);
    }

    /**
     * The same client over whatever transport the builder already carries, so a test can hand in one
     * {@code MockRestServiceServer} has bound. Package-private on purpose: the public method above is
     * the only way in from a feature, which keeps "a vendor client always has timeouts" a guarantee
     * the compiler enforces rather than a convention to remember.
     */
    RestClient createKeepingTransport(VendorClientSpec spec, RestClient.Builder builder,
                                      VendorRateLimiter rateLimiter) {
        return configure(spec, builder, rateLimiter);
    }

    private RestClient configure(VendorClientSpec spec, RestClient.Builder builder,
                                 VendorRateLimiter rateLimiter) {
        rateLimiter.pace(spec.vendor(), spec.requestsPerSecond());

        if (spec.authenticatesByHeader()) {
            builder.defaultHeader(spec.authHeader(), spec.authHeaderValue());
        }
        return builder
                .baseUrl(spec.baseUrl())
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().set(CorrelationId.HEADER, CorrelationId.current());
                    return execution.execute(withQueryToken(spec, request), body);
                })
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    throw new VendorResponseFailure(VendorFailureKind.of(response.getStatusCode()));
                })
                .build();
    }

    /**
     * The query-parameter key, added as the request leaves rather than where the URI is built. Spring's
     * transport failures quote the URI the adapter asked for, and that one has no key in it — so a
     * {@code ResourceAccessException} on its way into a log carries the path and never the credential.
     */
    private static HttpRequest withQueryToken(VendorClientSpec spec, HttpRequest request) {
        if (!spec.authenticatesByQuery()) {
            return request;
        }
        URI withToken = UriComponentsBuilder.fromUri(request.getURI())
                .queryParam(spec.queryTokenName(),
                        UriUtils.encodeQueryParam(spec.queryTokenValue(), StandardCharsets.UTF_8))
                .build(true)
                .toUri();
        return new HttpRequestWrapper(request) {
            @Override
            public URI getURI() {
                return withToken;
            }
        };
    }
}
