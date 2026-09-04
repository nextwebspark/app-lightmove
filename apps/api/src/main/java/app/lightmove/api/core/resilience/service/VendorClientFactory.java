package app.lightmove.api.core.resilience.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.ResilienceSettings;
import app.lightmove.api.core.logging.service.CorrelationId;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorClientSpec;
import java.net.http.HttpClient;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Builds the one kind of HTTP client this application points at a paid API: timeouts on both ends,
 * the key in a header, the correlation id carried across, and every non-2xx already classified before
 * the adapter sees it. A client built here cannot ship without a read timeout, and cannot leak a bare
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

        return builder
                .baseUrl(spec.baseUrl())
                .defaultHeader(spec.authHeader(), spec.authHeaderValue())
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().set(CorrelationId.HEADER, CorrelationId.current());
                    return execution.execute(request, body);
                })
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    throw new VendorResponseFailure(VendorFailureKind.of(response.getStatusCode()));
                })
                .build();
    }
}
