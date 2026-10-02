package app.lightmove.api.outreach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import app.lightmove.api.core.config.NylasSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.outreach.model.DeliveryFailure;
import app.lightmove.api.outreach.model.InboundMessage;
import app.lightmove.api.outreach.model.MailboxAccessWithdrawn;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * The hosted sign-in link Nylas is sent to, the answers it gives and the webhooks it signs, read back
 * against its documented shapes.
 */
class NylasMailboxGatewayTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String WEBHOOK_SECRET = "whsec_test";

    private final NylasMailboxGateway gateway = new NylasMailboxGateway(
            new NylasSettings("nyk_secret", "client-123", "https://api.us.nylas.com", List.of("google", "microsoft"), 5,
                    WEBHOOK_SECRET),
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

    @Test
    @DisplayName("a webhook delivery is read only when Nylas's signature over the raw body verifies")
    void aWebhookNeedsItsSignature() throws Exception {
        byte[] body = """
                {"type":"message.created","data":{"object":{"grant_id":"grant-9","thread_id":"thr-1",
                 "from":[{"email":"priya@target.example","name":"Priya"}],"body":"never read"}}}"""
                .getBytes(StandardCharsets.UTF_8);

        assertThat(gateway.readWebhook(signatureOf(body), body))
                .containsExactly(new InboundMessage("grant-9", "thr-1", "priya@target.example"));
        assertThat(gateway.readWebhook(signatureOf(body).toUpperCase(), body)).hasSize(1);
        assertThatThrownBy(() -> gateway.readWebhook("0".repeat(64), body))
                .isInstanceOfSatisfying(ApiException.class,
                        rejected -> assertThat(rejected.getCode()).isEqualTo(ErrorCode.MAILBOX_WEBHOOK_REJECTED));
        assertThatThrownBy(() -> gateway.readWebhook(null, body)).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("a bounce, a withdrawn grant and an event outreach does not listen to read as such")
    void webhookEventsReadAsTheirKind() throws Exception {
        byte[] bounce = """
                {"type":"message.bounce_detected","data":{"object":{"grant_id":"grant-9",
                 "bounced_address":"gone@target.example","origin":{"id":"msg-1","thread_id":"thr-1"}}}}"""
                .getBytes(StandardCharsets.UTF_8);
        byte[] expired = """
                {"type":"grant.expired","data":{"object":{"grant_id":"grant-9","code":25009}}}"""
                .getBytes(StandardCharsets.UTF_8);
        byte[] other = """
                {"type":"calendar.created","data":{"object":{"grant_id":"grant-9"}}}"""
                .getBytes(StandardCharsets.UTF_8);

        assertThat(gateway.readWebhook(signatureOf(bounce), bounce))
                .containsExactly(new DeliveryFailure("grant-9", "thr-1", "msg-1"));
        assertThat(gateway.readWebhook(signatureOf(expired), expired))
                .containsExactly(new MailboxAccessWithdrawn("grant-9"));
        assertThat(gateway.readWebhook(signatureOf(other), other)).isEmpty();
    }

    @Test
    @DisplayName("without a webhook secret every delivery is refused")
    void noSecretNoWebhook() throws Exception {
        NylasMailboxGateway unsigned = new NylasMailboxGateway(
                new NylasSettings("nyk_secret", "client-123", "https://api.us.nylas.com", List.of("google"), 5, ""),
                mock(VendorClientFactory.class), mock(VendorRateLimiter.class), mock(VendorCallGuard.class),
                RestClient.builder());
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> unsigned.readWebhook(signatureOf(body), body)).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("a thread's messages read back as their senders")
    void threadMessagesReadBack() {
        NylasMailboxGateway.ThreadMessages messages = JSON.readValue("""
                {"request_id":"r3","data":[{"from":[{"email":"consultant@firm.example"}]},
                 {"from":[{"name":"Priya","email":"priya@target.example"}]}]}""",
                NylasMailboxGateway.ThreadMessages.class);

        assertThat(messages.data()).extracting(message -> message.from().getFirst().email())
                .containsExactly("consultant@firm.example", "priya@target.example");
    }

    private static String signatureOf(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body));
    }
}
