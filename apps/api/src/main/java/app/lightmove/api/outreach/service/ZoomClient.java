package app.lightmove.api.outreach.service;

import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorClientSpec;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.outreach.model.ProviderCredentials;
import app.lightmove.api.outreach.model.ZoomMeeting;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

/** Zoom over HTTP: its OAuth host for consent and revoke, its REST API by the consultant's bearer token. */
public class ZoomClient implements ZoomApi {

    static final String VENDOR = "zoom";
    public static final String API = "https://api.zoom.us";
    public static final String OAUTH = "https://zoom.us";

    static final Duration READ_TIMEOUT = Duration.ofSeconds(20);
    private static final int REQUESTS_PER_SECOND = 10;

    /** Zoom's "scheduled meeting"; an instant one would start the moment it was made. */
    private static final int SCHEDULED_MEETING = 2;

    private final String oauthBaseUrl;
    private final RestClient api;
    private final RestClient oauth;
    private final VendorCallGuard guard;

    public ZoomClient(VendorClientFactory clientFactory, VendorRateLimiter rateLimiter, VendorCallGuard guard,
                      String apiBaseUrl, String oauthBaseUrl) {
        this.oauthBaseUrl = oauthBaseUrl;
        this.guard = guard;
        this.api = clientFactory.create(new VendorClientSpec(VENDOR, apiBaseUrl, null, null, null, null,
                READ_TIMEOUT, REQUESTS_PER_SECOND), RestClient.builder(), rateLimiter);
        this.oauth = clientFactory.create(new VendorClientSpec(VENDOR + "-oauth", oauthBaseUrl, null, null, null,
                null, READ_TIMEOUT, REQUESTS_PER_SECOND), RestClient.builder(), rateLimiter);
    }

    @Override
    public URI authorizationUri(ProviderCredentials app, String state, URI redirectUri) {
        return UriComponentsBuilder.fromUriString(oauthBaseUrl)
                .path("/oauth/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", app.clientId())
                .queryParam("redirect_uri", redirectUri.toString())
                .queryParam("state", state)
                .encode()
                .build()
                .toUri();
    }

    @Override
    public String userIdOf(String accessToken) {
        JsonNode me = guard.call(VendorCall.of(VENDOR, "me"), () -> api.get()
                .uri("/v2/users/me")
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class));
        String id = textOrNull(me == null ? null : me.get("id"));
        if (id == null) {
            throw new VendorException(VendorCall.of(VENDOR, "me"), VendorFailureKind.MALFORMED_RESPONSE, null);
        }
        return id;
    }

    /** Times go as UTC with no zone, which Zoom reads as given; the invite's own zone is the calendar's business. */
    @Override
    public ZoomMeeting createMeeting(String accessToken, String topic, Instant startsAt, int minutes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("topic", topic);
        body.put("type", SCHEDULED_MEETING);
        body.put("start_time", startsAt.truncatedTo(ChronoUnit.SECONDS).toString());
        body.put("duration", minutes);
        body.put("timezone", "UTC");
        JsonNode created = guard.call(VendorCall.of(VENDOR, "create-meeting"), () -> api.post()
                .uri("/v2/users/me/meetings")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class));
        String id = textOrNull(created == null ? null : created.get("id"));
        String joinUrl = textOrNull(created == null ? null : created.get("join_url"));
        if (id == null || joinUrl == null) {
            throw new VendorException(VendorCall.of(VENDOR, "create-meeting"), VendorFailureKind.MALFORMED_RESPONSE,
                    null);
        }
        return new ZoomMeeting(id, joinUrl);
    }

    @Override
    public void deleteMeeting(String accessToken, String meetingId) {
        guard.call(VendorCall.of(VENDOR, "delete-meeting"), () -> api.delete()
                .uri("/v2/meetings/{id}", meetingId)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public void revoke(ProviderCredentials app, String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", refreshToken);
        guard.call(VendorCall.of(VENDOR + "-oauth", "revoke"), () -> oauth.post()
                .uri("/oauth/revoke")
                .headers(headers -> headers.setBasicAuth(app.clientId(), app.clientSecret(), StandardCharsets.UTF_8))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity());
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() || node.asText().isBlank() ? null : node.asText();
    }
}
