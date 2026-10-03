package app.lightmove.api;

import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.outreach.model.ProviderCredentials;
import app.lightmove.api.outreach.model.ZoomMeeting;
import app.lightmove.api.outreach.service.ZoomApi;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.util.UriComponentsBuilder;

/** A Zoom that makes meetings in memory and remembers every one made, deleted and every token revoked. */
public class RecordingZoomApi implements ZoomApi {

    private final List<String> created = new CopyOnWriteArrayList<>();
    private final List<String> deleted = new CopyOnWriteArrayList<>();
    private final List<String> revoked = new CopyOnWriteArrayList<>();
    private final List<String> accessTokensUsed = new CopyOnWriteArrayList<>();
    private final AtomicInteger sequence = new AtomicInteger();
    private volatile String userId = "zoom-user-1";
    private volatile boolean failCreates;

    @Override
    public URI authorizationUri(ProviderCredentials app, String state, URI redirectUri) {
        return UriComponentsBuilder.fromUriString("https://zoom.example/oauth/authorize")
                .queryParam("client_id", app.clientId())
                .queryParam("state", state)
                .build()
                .toUri();
    }

    @Override
    public String userIdOf(String accessToken) {
        return userId;
    }

    @Override
    public ZoomMeeting createMeeting(String accessToken, String topic, Instant startsAt, int minutes) {
        accessTokensUsed.add(accessToken);
        if (failCreates) {
            throw new VendorException(VendorCall.of("zoom", "create-meeting"), VendorFailureKind.UNAVAILABLE, null);
        }
        String id = "8" + (100000000 + sequence.incrementAndGet());
        created.add(id);
        return new ZoomMeeting(id, "https://us05web.zoom.us/j/" + id + "?pwd=abc");
    }

    @Override
    public void deleteMeeting(String accessToken, String meetingId) {
        deleted.add(meetingId);
    }

    @Override
    public void revoke(ProviderCredentials app, String refreshToken) {
        revoked.add(refreshToken);
    }

    public void answerAsUser(String zoomUserId) {
        this.userId = zoomUserId;
    }

    public void failCreates(boolean fail) {
        this.failCreates = fail;
    }

    public List<String> created() {
        return List.copyOf(created);
    }

    public List<String> deleted() {
        return List.copyOf(deleted);
    }

    public List<String> revoked() {
        return List.copyOf(revoked);
    }

    public List<String> accessTokensUsed() {
        return List.copyOf(accessTokensUsed);
    }

    public void clear() {
        created.clear();
        deleted.clear();
        revoked.clear();
        accessTokensUsed.clear();
        userId = "zoom-user-1";
        failCreates = false;
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        @Primary
        public RecordingZoomApi recordingZoomApi() {
            return new RecordingZoomApi();
        }
    }
}
