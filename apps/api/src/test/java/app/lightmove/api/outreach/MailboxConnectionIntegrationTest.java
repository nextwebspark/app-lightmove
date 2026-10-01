package app.lightmove.api.outreach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingMailboxGateway;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.outreach.model.GrantedMailbox;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Connecting a consultant's own mailbox: the hosted sign-in round trip, the test send, letting it go,
 * and the refusals that keep one person's consent from landing on someone else's account.
 */
@IntegrationTest
class MailboxConnectionIntegrationTest extends FlowTestSupport {

    private static final String MAILBOX = "/api/v1/outreach/mailbox";
    private static final String CALLBACK = MAILBOX + "/callback";

    @Autowired private RecordingMailboxGateway gateway;

    private String consultant;

    @BeforeEach
    void signInConsultant() throws Exception {
        gateway.clear();
        String consultantEmail = "yara@" + domain;
        createWorkspace(verifiedUser("Yara Haddad", consultantEmail), "Meridian Search");
        consultant = login(consultantEmail);
    }

    @Test
    @DisplayName("before anything is connected, the mailbox answers what can be connected and nothing else")
    void nothingConnectedYet() throws Exception {
        mvc.perform(get(MAILBOX).header("Authorization", "Bearer " + consultant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offered").value(true))
                .andExpect(jsonPath("$.providers[0]").value("google"))
                .andExpect(jsonPath("$.connection").isEmpty());
    }

    @Test
    @DisplayName("the consent screen's answer, from the browser that asked, connects the mailbox")
    void aRoundTripConnects() throws Exception {
        ConnectAttempt attempt = startConnect("google");

        MvcResult landed = mvc.perform(get(CALLBACK)
                        .param("state", attempt.state()).param("code", "code-1")
                        .cookie(attempt.cookie()))
                .andExpect(status().isFound())
                .andReturn();

        assertThat(landed.getResponse().getRedirectedUrl()).endsWith("/outreach/mailbox/callback?status=connected");
        assertThat(gateway.redeemedCodes()).containsExactly("code-1");
        mvc.perform(get(MAILBOX).header("Authorization", "Bearer " + consultant))
                .andExpect(jsonPath("$.connection.address").value("consultant@firm.example"))
                .andExpect(jsonPath("$.connection.provider").value("google"))
                .andExpect(jsonPath("$.connection.status").value("ACTIVE"))
                .andExpect(jsonPath("$.connection.dailyCap").value(50))
                .andExpect(jsonPath("$.connection.grantId").doesNotExist());
    }

    @Test
    @DisplayName("a consent link opened in another browser connects nothing")
    void aForwardedLinkConnectsNothing() throws Exception {
        ConnectAttempt attempt = startConnect("google");

        MvcResult landed = mvc.perform(get(CALLBACK).param("state", attempt.state()).param("code", "code-1"))
                .andExpect(status().isFound())
                .andReturn();

        assertThat(landed.getResponse().getRedirectedUrl()).endsWith("?error=MAILBOX_CONNECT_EXPIRED");
        assertThat(gateway.redeemedCodes()).isEmpty();
        mvc.perform(get(MAILBOX).header("Authorization", "Bearer " + consultant))
                .andExpect(jsonPath("$.connection").isEmpty());
    }

    @Test
    @DisplayName("a state is redeemed once: replaying the callback connects nothing more")
    void aStateIsRedeemedOnce() throws Exception {
        ConnectAttempt attempt = startConnect("google");
        callBack(attempt, "code-1");

        MvcResult replayed = callBack(attempt, "code-2");

        assertThat(replayed.getResponse().getRedirectedUrl()).endsWith("?error=MAILBOX_CONNECT_EXPIRED");
        assertThat(gateway.redeemedCodes()).containsExactly("code-1");
    }

    @Test
    @DisplayName("only the newest attempt counts: starting again drops the one before")
    void startingAgainDropsTheEarlierAttempt() throws Exception {
        ConnectAttempt first = startConnect("google");
        startConnect("microsoft");

        assertThat(callBack(first, "code-1").getResponse().getRedirectedUrl())
                .endsWith("?error=MAILBOX_CONNECT_EXPIRED");
    }

    @Test
    @DisplayName("pressing Cancel on the provider's screen comes back as a cancel, not a failure")
    void cancellingIsNotFailing() throws Exception {
        ConnectAttempt attempt = startConnect("google");

        MvcResult landed = mvc.perform(get(CALLBACK)
                        .param("state", attempt.state()).param("error", "access_denied")
                        .cookie(attempt.cookie()))
                .andReturn();

        assertThat(landed.getResponse().getRedirectedUrl()).endsWith("?error=MAILBOX_CONNECT_CANCELLED");
    }

    @Test
    @DisplayName("a host the deployment does not list cannot be connected")
    void anUnlistedHostIsRefused() throws Exception {
        MvcResult refused = mvc.perform(post(MAILBOX + "/connect")
                        .header("Authorization", "Bearer " + consultant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"yahoo"}"""))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(codeOf(refused)).isEqualTo("MAILBOX_PROVIDER_UNSUPPORTED");
    }

    @Test
    @DisplayName("the test email goes from the connected mailbox to itself")
    void aTestSendGoesToTheMailboxItself() throws Exception {
        connect();

        mvc.perform(post(MAILBOX + "/test").header("Authorization", "Bearer " + consultant))
                .andExpect(status().isNoContent());

        assertThat(gateway.sent()).singleElement().satisfies(sent -> {
            assertThat(sent.grantId()).isEqualTo("grant-1");
            assertThat(sent.email().to()).isEqualTo("consultant@firm.example");
        });
    }

    @Test
    @DisplayName("a mailbox whose access was withdrawn is marked for reconnecting, and says so")
    void aWithdrawnGrantNeedsReconnecting() throws Exception {
        connect();
        gateway.failSendsWith(new VendorException(VendorCall.of("nylas", "send"), VendorFailureKind.CREDENTIALS, null));

        MvcResult refused = mvc.perform(post(MAILBOX + "/test").header("Authorization", "Bearer " + consultant))
                .andExpect(status().isConflict())
                .andReturn();

        assertThat(codeOf(refused)).isEqualTo("MAILBOX_RECONNECT_NEEDED");
        mvc.perform(get(MAILBOX).header("Authorization", "Bearer " + consultant))
                .andExpect(jsonPath("$.connection.status").value("ERROR"));
    }

    @Test
    @DisplayName("reconnecting replaces the mailbox and lets the old grant go")
    void reconnectingReplacesTheGrant() throws Exception {
        connect();
        gateway.grant(new GrantedMailbox("grant-2", "yara.haddad@firm.example", "microsoft"));

        connect();

        assertThat(gateway.revoked()).containsExactly("grant-1");
        mvc.perform(get(MAILBOX).header("Authorization", "Bearer " + consultant))
                .andExpect(jsonPath("$.connection.address").value("yara.haddad@firm.example"))
                .andExpect(jsonPath("$.connection.provider").value("microsoft"));
    }

    @Test
    @DisplayName("disconnecting forgets the mailbox and withdraws the mail service's access")
    void disconnectingLetsItGo() throws Exception {
        connect();

        mvc.perform(delete(MAILBOX).header("Authorization", "Bearer " + consultant))
                .andExpect(status().isNoContent());

        assertThat(gateway.revoked()).containsExactly("grant-1");
        mvc.perform(get(MAILBOX).header("Authorization", "Bearer " + consultant))
                .andExpect(jsonPath("$.connection").isEmpty());
    }

    @Test
    @DisplayName("a colleague sees their own mailbox, never someone else's")
    void aMailboxIsItsOwnersAlone() throws Exception {
        connect();
        String colleagueEmail = "dana@" + domain;
        inviteAndAccept(consultant, "Dana Aboud", colleagueEmail, "MEMBER");

        mvc.perform(get(MAILBOX).header("Authorization", "Bearer " + login(colleagueEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connection").isEmpty());
    }

    @Test
    @DisplayName("without a mail service, nothing is offered and nothing can be started")
    void unconfiguredOffersNothing() throws Exception {
        gateway.offer(false);

        mvc.perform(get(MAILBOX).header("Authorization", "Bearer " + consultant))
                .andExpect(jsonPath("$.offered").value(false));
        MvcResult refused = mvc.perform(post(MAILBOX + "/connect")
                        .header("Authorization", "Bearer " + consultant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"google"}"""))
                .andExpect(status().isConflict())
                .andReturn();
        assertThat(codeOf(refused)).isEqualTo("MAILBOX_UNAVAILABLE");
    }

    @Test
    @DisplayName("a client representative has no mailbox to connect")
    void aClientRepresentativeIsRefused() throws Exception {
        String repEmail = "rep@client-" + domain;
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + consultant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Acme Corp"}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        mvc.perform(post("/api/v1/clients/" + clientId + "/representatives")
                        .header("Authorization", "Bearer " + consultant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Rep Person","position":"Advisor","email":"%s"}
                                """.formatted(repEmail)))
                .andExpect(status().isCreated());
        String rep = body(mvc.perform(post("/api/v1/onboarding/accept-invitation-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","fullName":"Rep Person","password":"%s"}
                                """.formatted(email.latestTokenFor(repEmail), PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn()).get("accessToken").asText();

        mvc.perform(get(MAILBOX).header("Authorization", "Bearer " + rep))
                .andExpect(status().isForbidden());
    }

    private void connect() throws Exception {
        callBack(startConnect("google"), "code-" + System.nanoTime());
    }

    private ConnectAttempt startConnect(String provider) throws Exception {
        MvcResult started = mvc.perform(post(MAILBOX + "/connect")
                        .header("Authorization", "Bearer " + consultant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"%s"}""".formatted(provider)))
                .andExpect(status().isOk())
                .andReturn();
        URI authorization = URI.create(body(started).get("authorizationUrl").asText());
        String state = UriComponentsBuilder.fromUri(authorization).build().getQueryParams().getFirst("state");
        Cookie cookie = started.getResponse().getCookie("lm_mailbox_connect");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isEqualTo(state);
        return new ConnectAttempt(state, cookie);
    }

    private MvcResult callBack(ConnectAttempt attempt, String code) throws Exception {
        return mvc.perform(get(CALLBACK)
                        .param("state", attempt.state()).param("code", code)
                        .cookie(attempt.cookie()))
                .andExpect(status().isFound())
                .andReturn();
    }

    private record ConnectAttempt(String state, Cookie cookie) {}
}
