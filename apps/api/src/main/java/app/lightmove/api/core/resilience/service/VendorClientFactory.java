package app.lightmove.api.core.resilience.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.ResilienceSettings;
import app.lightmove.api.core.logging.service.CorrelationId;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorClientSpec;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.JsonNode;

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

    private static final List<String> REQUEST_ID_HEADERS = List.of("request-id", "x-request-id");
    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9-]{1,80}");

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
                    throw failureOf(response);
                })
                .build();
    }

    /**
     * A refusal with no code in its body is described by its shape, content type and the vendor's request id, so a
     * 400 that is not the vendor's own answer — a gateway's page, an empty reply — still reads as what it was.
     */
    private static VendorResponseFailure failureOf(ClientHttpResponse response) throws IOException {
        VendorFailureKind kind = VendorFailureKind.of(response.getStatusCode());
        byte[] body = errorBodyOrNull(response);
        JsonNode json = VendorErrorCodes.jsonOrNull(body);
        String code = VendorErrorCodes.codeOrNull(json);
        if (code != null) {
            return new VendorResponseFailure(kind, code, code);
        }
        HttpHeaders headers = response.getHeaders();
        StringBuilder detail = new StringBuilder("no code; ").append(VendorErrorShape.of(body, json));
        MediaType contentType = headers.getContentType();
        if (contentType != null) {
            detail.append("; ").append(contentType.getType()).append('/').append(contentType.getSubtype());
        }
        for (String requestIdHeader : REQUEST_ID_HEADERS) {
            String requestId = headers.getFirst(requestIdHeader);
            if (requestId != null && REQUEST_ID.matcher(requestId).matches()) {
                detail.append("; ").append(requestIdHeader).append(' ').append(requestId);
                break;
            }
        }
        return new VendorResponseFailure(kind, null, detail.toString());
    }

    private static byte[] errorBodyOrNull(ClientHttpResponse response) {
        try (InputStream body = response.getBody()) {
            return body.readNBytes(VendorErrorCodes.MAX_ERROR_BODY_BYTES);
        } catch (IOException unreadable) {
            return null;
        }
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
