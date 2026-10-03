package app.lightmove.api.outreach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.ResilienceSettings;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.outreach.constant.CredentialMode;
import app.lightmove.api.outreach.constant.IntegrationProvider;
import app.lightmove.api.outreach.model.GrantedMailbox;
import app.lightmove.api.outreach.model.MailboxGrants;
import app.lightmove.api.outreach.model.OutgoingEmail;
import app.lightmove.api.outreach.model.ProviderCredentials;
import app.lightmove.api.outreach.model.SentEmail;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Gmail gateway against recorded Google answers: the consent link, a redeemed code, a first email and a threaded
 * follow-up, who wrote into a thread, a revoke by token, and the refusals told apart.
 */
class GoogleMailboxGatewayTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final URI CALLBACK = URI.create("https://beta.uncava.com/api/v1/outreach/mailbox/callback");
    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final ProviderCredentials SHARED = new ProviderCredentials(IntegrationProvider.GOOGLE,
            CredentialMode.SHARED, "uncava-google-client", "uncava-google-secret", null);

    private final ProviderCredentialsResolver resolver = mock(ProviderCredentialsResolver.class);
    private final MailboxTokens mailboxTokens = mock(MailboxTokens.class);
    private final RecordedGoogle google = new RecordedGoogle();

    private HttpServer server;
    private GoogleMailboxGateway gateway;
    private OAuthProviderTokenClient tokenEndpoint;

    @BeforeEach
    void startRecordedGoogle() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", google::answer);
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();

        LightMoveProperties properties = mock(LightMoveProperties.class);
        when(properties.resilience()).thenReturn(new ResilienceSettings(Duration.ofSeconds(2), 0,
                Duration.ofMillis(1), 1.0, Duration.ZERO, Duration.ofMillis(1), Duration.ofSeconds(1)));
        VendorRateLimiter limiter = new VendorRateLimiter();
        VendorClientFactory factory = new VendorClientFactory(properties);
        VendorCallGuard guard = new VendorCallGuard(limiter, properties);
        tokenEndpoint = new OAuthProviderTokenClient(factory, limiter, guard, Map.of(
                IntegrationProvider.GOOGLE, base + "/oauth2", IntegrationProvider.MICROSOFT, base + "/login",
                IntegrationProvider.ZOOM, base + "/zoom"));
        gateway = new GoogleMailboxGateway(resolver, tokenEndpoint, mailboxTokens, factory, limiter, guard,
                base + "/api", base + "/accounts");
        when(resolver.resolve(WORKSPACE, IntegrationProvider.GOOGLE)).thenReturn(Optional.of(SHARED));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    @DisplayName("the consent link asks for offline access with a consent screen, so a refresh token comes back")
    void theConsentLinkAsksForOfflineAccess() {
        URI link = gateway.authorizationUri(WORKSPACE, "google", "yara@meridian.example", "state-1", CALLBACK);
        MultiValueMap<String, String> query = UriComponentsBuilder.fromUri(link).build().getQueryParams();

        assertThat(link.getPath()).isEqualTo("/accounts/o/oauth2/v2/auth");
        assertThat(query.getFirst("client_id")).isEqualTo("uncava-google-client");
        assertThat(query.getFirst("access_type")).isEqualTo("offline");
        assertThat(query.getFirst("prompt")).isEqualTo("consent");
        assertThat(decoded(query.getFirst("scope"))).contains("https://www.googleapis.com/auth/gmail.send",
                "https://www.googleapis.com/auth/gmail.metadata", "https://www.googleapis.com/auth/calendar.events");
        assertThat(query.getFirst("state")).isEqualTo("state-1");
        assertThat(link.toString()).doesNotContain("uncava-google-secret");
    }

    @Test
    @DisplayName("a redeemed code is a direct grant for the profile's address, holding the refresh token")
    void aCodeRedeemsToADirectGrant() {
        GrantedMailbox granted = gateway.redeem(WORKSPACE, "google", "4/0Ab-code", CALLBACK);

        assertThat(MailboxGrants.directProviderOf(granted.grantId())).contains("google");
        assertThat(granted.address()).isEqualTo("yara.haddad@meridian.example");
        assertThat(granted.refreshToken()).isEqualTo("1//0g-refresh");
        assertThat(google.lastForm()).containsEntry("grant_type", "authorization_code")
                .containsEntry("code", "4/0Ab-code").containsEntry("client_secret", "uncava-google-secret")
                .doesNotContainKey("scope");
        assertThat(google.authorizationOf("/api/gmail/v1/users/me/profile")).isEqualTo("Bearer ya29.access");
    }

    @Test
    @DisplayName("a first email is one raw send, answering Gmail's message and thread ids")
    void aFirstEmailIsSent() throws Exception {
        String grantId = MailboxGrants.mintDirect("google");
        when(mailboxTokens.accessToken(grantId)).thenReturn("access-for-yara");

        SentEmail sent = gateway.send(grantId, new OutgoingEmail("priya@client.example", "A CFO role", "<p>Hi</p>"));

        assertThat(sent.messageId()).isEqualTo("18c2a1f0e5d4b3a2");
        assertThat(sent.threadId()).isEqualTo("18c2a1f0e5d4b3a2");
        JsonNode body = JSON.readTree(google.bodyOf("POST /api/gmail/v1/users/me/messages/send"));
        assertThat(body.has("threadId")).isFalse();
        String raw = new String(Base64.getUrlDecoder().decode(body.path("raw").asString("")), StandardCharsets.UTF_8);
        assertThat(raw).contains("To: priya@client.example").contains("Subject: A CFO role")
                .doesNotContain("In-Reply-To");
    }

    @Test
    @DisplayName("a follow-up names the thread and replies to the last message's own Message-ID")
    void aFollowUpThreads() throws Exception {
        String grantId = MailboxGrants.mintDirect("google");
        when(mailboxTokens.accessToken(grantId)).thenReturn("access-for-yara");

        gateway.send(grantId, new OutgoingEmail("priya@client.example", "Re: A CFO role", "<p>Following up</p>",
                "18c2a1f0e5d4b3a2"));

        assertThat(google.lastQueryOf("/api/gmail/v1/users/me/messages/18c2a1f0e5d4b3a2"))
                .contains("format=metadata").contains("metadataHeaders=Message-ID");
        JsonNode body = JSON.readTree(google.bodyOf("POST /api/gmail/v1/users/me/messages/send"));
        assertThat(body.path("threadId").asString("")).isEqualTo("18c2a1f0e5d4b3a2");
        String raw = new String(Base64.getUrlDecoder().decode(body.path("raw").asString("")), StandardCharsets.UTF_8);
        assertThat(raw).contains("In-Reply-To: <CAB2+first@mail.gmail.com>")
                .contains("References: <CAB2+first@mail.gmail.com>");
    }

    @Test
    @DisplayName("who wrote into a thread is read from From headers, never the consultant's own sent mail or drafts")
    void threadSendersAreAddressesOnly() {
        String grantId = MailboxGrants.mintDirect("google");
        when(mailboxTokens.accessToken(grantId)).thenReturn("access-for-yara");

        List<String> writers = gateway.senderAddressesInThread(grantId, "18c2a1f0e5d4b3a2",
                Instant.parse("2026-10-01T00:00:00Z"));

        assertThat(writers).containsExactly("priya@client.example", "mailer-daemon@googlemail.com");
        assertThat(google.lastQueryOf("/api/gmail/v1/users/me/threads/18c2a1f0e5d4b3a2"))
                .contains("format=metadata").contains("metadataHeaders=From");
    }

    @Test
    @DisplayName("a revoke forgets the token in memory and revokes the refresh token at Google")
    void revokeWithdrawsTheRefreshToken() {
        String grantId = MailboxGrants.mintDirect("google");

        gateway.revoke(grantId, "1//0g-refresh");

        verify(mailboxTokens).forget(grantId);
        assertThat(google.requested()).contains("POST /oauth2/revoke");
        assertThat(google.lastForm()).containsEntry("token", "1//0g-refresh");
    }

    @Test
    @DisplayName("Google's refusals are told apart: a dead grant, a refused app, and throttling")
    void refusalsAreToldApart() {
        google.refreshAnswers(400, "{\"error\":\"invalid_grant\",\"error_description\":\"Token has been expired or revoked.\"}");
        assertThatThrownBy(() -> tokenEndpoint.refresh(SHARED, "1//0g-refresh"))
                .isInstanceOf(ProviderGrantRefused.class);

        google.refreshAnswers(401, "{\"error\":\"invalid_client\",\"error_description\":\"Unauthorized\"}");
        assertThatThrownBy(() -> tokenEndpoint.refresh(SHARED, "1//0g-refresh"))
                .isInstanceOf(ProviderAppUnavailable.class);

        String grantId = MailboxGrants.mintDirect("google");
        when(mailboxTokens.accessToken(grantId)).thenReturn("access-for-yara");
        google.sendAnswers(429);
        assertThatThrownBy(() -> gateway.send(grantId, new OutgoingEmail("priya@client.example", "Hi", "<p>Hi</p>")))
                .isInstanceOfSatisfying(VendorException.class,
                        failed -> assertThat(failed.getKind()).isEqualTo(VendorFailureKind.RATE_LIMITED));
    }

    @Test
    @DisplayName("offered only where some app exists; per workspace, only where that one has one")
    void offeredOnlyWhereAnAppExists() {
        when(resolver.isAnyAppAt(IntegrationProvider.GOOGLE)).thenReturn(false);
        assertThat(gateway.isOffered()).isFalse();
        UUID withoutApp = UUID.randomUUID();
        when(resolver.resolve(withoutApp, IntegrationProvider.GOOGLE)).thenReturn(Optional.empty());
        assertThat(gateway.isOfferedTo(WORKSPACE)).isTrue();
        assertThat(gateway.isOfferedTo(withoutApp)).isFalse();
    }

    private static String decoded(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    /** Google's answers, as its API reference records them, and every request it was sent. */
    private static final class RecordedGoogle {

        private final List<String> requested = new CopyOnWriteArrayList<>();
        private final Map<String, String> bodies = new ConcurrentHashMap<>();
        private final Map<String, String> queries = new ConcurrentHashMap<>();
        private final Map<String, String> authorizations = new ConcurrentHashMap<>();
        private volatile String lastForm = "";
        private volatile int refreshStatus = 200;
        private volatile String refreshBody;
        private volatile int sendStatus = 200;

        void answer(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String key = exchange.getRequestMethod() + " " + path;
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requested.add(key);
            bodies.put(key, body);
            queries.put(path, exchange.getRequestURI().getQuery() == null ? "" : exchange.getRequestURI().getQuery());
            authorizations.put(path, String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));

            if (path.equals("/oauth2/token")) {
                lastForm = body;
                if (body.contains("grant_type=refresh_token") && refreshBody != null) {
                    respond(exchange, refreshStatus, refreshBody);
                    return;
                }
                respond(exchange, 200, """
                        {"access_token":"ya29.access","expires_in":3599,"refresh_token":"1//0g-refresh",
                         "scope":"https://www.googleapis.com/auth/gmail.send openid","token_type":"Bearer",
                         "id_token":"eyJhbGciOi.unused"}""");
            } else if (path.equals("/oauth2/revoke")) {
                lastForm = body;
                respond(exchange, 200, "");
            } else if (path.equals("/api/gmail/v1/users/me/profile")) {
                respond(exchange, 200, """
                        {"emailAddress":"yara.haddad@meridian.example","messagesTotal":4182,"threadsTotal":2210,
                         "historyId":"9876543"}""");
            } else if (path.equals("/api/gmail/v1/users/me/messages/send")) {
                respond(exchange, sendStatus, sendStatus != 200 ? "{\"error\":{\"code\":" + sendStatus + "}}" : """
                        {"id":"18c2a1f0e5d4b3a2","threadId":"18c2a1f0e5d4b3a2","labelIds":["SENT"]}""");
            } else if (path.equals("/api/gmail/v1/users/me/messages/18c2a1f0e5d4b3a2")) {
                respond(exchange, 200, """
                        {"id":"18c2a1f0e5d4b3a2","threadId":"18c2a1f0e5d4b3a2","labelIds":["SENT"],
                         "payload":{"headers":[{"name":"Message-Id","value":"<CAB2+first@mail.gmail.com>"}]}}""");
            } else if (path.equals("/api/gmail/v1/users/me/threads/18c2a1f0e5d4b3a2")) {
                respond(exchange, 200, """
                        {"id":"18c2a1f0e5d4b3a2","messages":[
                          {"id":"m0","labelIds":["INBOX"],"internalDate":"1789000000000",
                           "payload":{"headers":[{"name":"From","value":"Old <someone-earlier@client.example>"}]}},
                          {"id":"m1","labelIds":["SENT"],"internalDate":"1790845200000",
                           "payload":{"headers":[{"name":"From","value":"Yara Haddad <yhaddad@meridian.example>"}]}},
                          {"id":"m2","labelIds":["INBOX","UNREAD"],"internalDate":"1790940600000",
                           "payload":{"headers":[{"name":"From","value":"\\"Priya Raman\\" <Priya@Client.example>"}]}},
                          {"id":"m3","labelIds":["DRAFT"],"internalDate":"1790940700000",
                           "payload":{"headers":[{"name":"From","value":"Yara Haddad <yara.haddad@meridian.example>"}]}},
                          {"id":"m4","labelIds":["INBOX"],"internalDate":"1790940800000",
                           "payload":{"headers":[{"name":"From","value":"Mail Delivery Subsystem <mailer-daemon@googlemail.com>"}]}}
                        ]}""");
            } else {
                respond(exchange, 404, "{\"error\":{\"code\":404}}");
            }
        }

        void refreshAnswers(int status, String body) {
            this.refreshStatus = status;
            this.refreshBody = body;
        }

        void sendAnswers(int status) {
            this.sendStatus = status;
        }

        Map<String, String> lastForm() {
            return UriComponentsBuilder.newInstance().query(lastForm).build().getQueryParams().toSingleValueMap()
                    .entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                            entry -> URLDecoder.decode(entry.getValue(), StandardCharsets.UTF_8)));
        }

        List<String> requested() {
            return List.copyOf(requested);
        }

        String bodyOf(String key) {
            return bodies.get(key);
        }

        String lastQueryOf(String path) {
            return queries.getOrDefault(path, "");
        }

        String authorizationOf(String path) {
            return authorizations.get(path);
        }

        private static void respond(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        }
    }
}
