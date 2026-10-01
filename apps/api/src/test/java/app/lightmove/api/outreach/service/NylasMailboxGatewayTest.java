package app.lightmove.api.outreach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import app.lightmove.api.core.config.NylasSettings;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

/** The hosted sign-in link Nylas is sent to, and the answers it gives read back, against its documented shapes. */
class NylasMailboxGatewayTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final NylasMailboxGateway gateway = new NylasMailboxGateway(
            new NylasSettings("nyk_secret", "client-123", "https://api.us.nylas.com", List.of("google", "microsoft"), 5),
            mock(VendorClientFactory.class), mock(VendorRateLimiter.class), mock(VendorCallGuard.class),
            RestClient.builder());

    @Test
    @DisplayName("the sign-in link names our application, the host, the callback and the state, and never the key")
    void theSignInLinkCarriesWhatNylasAsksFor() {
        URI link = gateway.authorizationUri("google", "yara@firm.example", "state-abc",
                URI.create("https://beta.uncava.com/api/v1/outreach/mailbox/callback"));

        assertThat(link.getHost()).isEqualTo("api.us.nylas.com");
        assertThat(link.getPath()).isEqualTo("/v3/connect/auth");
        MultiValueMap<String, String> query = UriComponentsBuilder.fromUri(link).build().getQueryParams();
        assertThat(query.getFirst("client_id")).isEqualTo("client-123");
        assertThat(query.getFirst("provider")).isEqualTo("google");
        assertThat(query.getFirst("response_type")).isEqualTo("code");
        assertThat(query.getFirst("state")).isEqualTo("state-abc");
        assertThat(link.toString()).doesNotContain("nyk_secret");
        assertThat(URLDecoder.decode(query.getFirst("redirect_uri"), StandardCharsets.UTF_8))
                .isEqualTo("https://beta.uncava.com/api/v1/outreach/mailbox/callback");
    }

    @Test
    @DisplayName("the token answer and the grant read back as Nylas spells them")
    void answersReadBack() {
        NylasMailboxGateway.TokenAnswer token = JSON.readValue("""
                {"access_token":"at","token_type":"Bearer","id_token":"it","grant_id":"grant-9"}""",
                NylasMailboxGateway.TokenAnswer.class);
        NylasMailboxGateway.GrantAnswer grant = JSON.readValue("""
                {"request_id":"r1","data":{"id":"grant-9","provider":"microsoft","email":"yara@firm.example",
                 "grant_status":"valid"}}""", NylasMailboxGateway.GrantAnswer.class);
        NylasMailboxGateway.SendAnswer sent = JSON.readValue("""
                {"request_id":"r2","data":{"id":"msg-1","thread_id":"thr-1","subject":"Hello"}}""",
                NylasMailboxGateway.SendAnswer.class);

        assertThat(token.grantId()).isEqualTo("grant-9");
        assertThat(grant.data().email()).isEqualTo("yara@firm.example");
        assertThat(grant.data().provider()).isEqualTo("microsoft");
        assertThat(sent.data().threadId()).isEqualTo("thr-1");
    }
}
