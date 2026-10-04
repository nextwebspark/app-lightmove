package app.lightmove.api.core.resilience.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.ResilienceSettings;
import app.lightmove.api.core.logging.service.CorrelationId;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorClientSpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

/**
 * Builds every paid-API client: timeouts on both ends, the key, the correlation id, and every non-2xx
 * classified before the adapter sees it.
 *
 * <p>The status handler covers <i>every</i> error status as a privacy control: Spring's own handler puts
 * the body — which echoes the query, a researched person's name — into the exception message. The
 * builder is a parameter because it mutates in place and would carry one vendor's key into the next.
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

    /** For a test's bound transport; package-private so a feature cannot get a client without timeouts. */
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
     * Added as the request leaves, not where the URI is built: Spring's transport failures quote the
     * adapter's URI, so a logged {@code ResourceAccessException} never carries the credential.
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
