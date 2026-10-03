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
import app.lightmove.api.outreach.model.ReleasedGrant;
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
 * The Graph gateway against recorded Microsoft answers: the consent link, a redeemed code, a first email and a
 * threaded follow-up, who wrote into a conversation, and a refresh Microsoft refuses.
 */
class MicrosoftMailboxGatewayTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final URI CALLBACK = URI.create("https://beta.uncava.com/api/v1/outreach/mailbox/callback");
    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final ProviderCredentials SHARED = new ProviderCredentials(IntegrationProvider.MICROSOFT,
            CredentialMode.SHARED, "uncava-microsoft-client", "uncava-secret", null);
    private static final ProviderCredentials OWN = new ProviderCredentials(IntegrationProvider.MICROSOFT,
            CredentialMode.OWN, "firm-client", "firm-secret", "8f3a6c1e-2b4d-4e5f-9a0b-1c2d3e4f5a6b");

    private final ProviderCredentialsResolver resolver = mock(ProviderCredentialsResolver.class);
    private final MailboxTokens mailboxTokens = mock(MailboxTokens.class);
    private final RecordedMicrosoft microsoft = new RecordedMicrosoft();

    private HttpServer server;
    private MicrosoftMailboxGateway gateway;
    private OAuthProviderTokenClient tokenEndpoint;

    @BeforeEach
    void startRecordedMicrosoft() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", microsoft::answer);
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();

        LightMoveProperties properties = mock(LightMoveProperties.class);
        when(properties.resilience()).thenReturn(new ResilienceSettings(Duration.ofSeconds(2), 0,
                Duration.ofMillis(1), 1.0, Duration.ZERO, Duration.ofMillis(1), Duration.ofSeconds(1)));
        VendorRateLimiter limiter = new VendorRateLimiter();
        VendorClientFactory factory = new VendorClientFactory(properties);
        VendorCallGuard guard = new VendorCallGuard(limiter, properties);
        tokenEndpoint = new OAuthProviderTokenClient(factory, limiter, guard,
                Map.of(IntegrationProvider.GOOGLE, base + "/google", IntegrationProvider.MICROSOFT, base + "/login",
                        IntegrationProvider.ZOOM, base + "/zoom"));
        gateway = new MicrosoftMailboxGateway(resolver, tokenEndpoint, mailboxTokens, factory, limiter, guard,
                base + "/graph", base + "/login");
        when(resolver.resolve(WORKSPACE, IntegrationProvider.MICROSOFT)).thenReturn(Optional.of(SHARED));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    @DisplayName("the consent link signs in at any organisation for the shared app, at the firm's directory for its own")
    void theConsentLinkNamesTheRightDirectory() {
        URI shared = gateway.authorizationUri(WORKSPACE, "microsoft", "yara@firm.example", "state-1", CALLBACK);
        MultiValueMap<String, String> query = UriComponentsBuilder.fromUri(shared).build().getQueryParams();
        assertThat(shared.getPath()).endsWith("/login/organizations/oauth2/v2.0/authorize");
        assertThat(query.getFirst("client_id")).isEqualTo("uncava-microsoft-client");
        assertThat(decoded(query.getFirst("scope")))
                .isEqualTo("offline_access User.Read Mail.ReadWrite Mail.Send Calendars.ReadWrite");
        assertThat(decoded(query.getFirst("redirect_uri"))).isEqualTo(CALLBACK.toString());
        assertThat(query.getFirst("state")).isEqualTo("state-1");
        assertThat(shared.toString()).doesNotContain("uncava-secret");

        when(resolver.resolve(WORKSPACE, IntegrationProvider.MICROSOFT)).thenReturn(Optional.of(OWN));
        URI own = gateway.authorizationUri(WORKSPACE, "microsoft", null, "state-2", CALLBACK);
        assertThat(own.getPath()).endsWith("/login/" + OWN.tenantId() + "/oauth2/v2.0/authorize");
        assertThat(UriComponentsBuilder.fromUri(own).build().getQueryParams().getFirst("client_id"))
                .isEqualTo("firm-client");
    }

    @Test
    @DisplayName("a redeemed code is a direct grant for the signed-in address, holding the refresh token")
    void aCodeRedeemsToADirectGrant() {
        GrantedMailbox granted = gateway.redeem(WORKSPACE, "microsoft", "code-1", CALLBACK);

        assertThat(MailboxGrants.isDirect(granted.grantId())).isTrue();
        assertThat(MailboxGrants.directProviderOf(granted.grantId())).contains("microsoft");
        assertThat(granted.address()).isEqualTo("yara.haddad@meridian.example");
        assertThat(granted.provider()).isEqualTo("microsoft");
        assertThat(granted.refreshToken()).isEqualTo("M.C123_refresh");
        Map<String, String> form = microsoft.lastForm();
        assertThat(form).containsEntry("grant_type", "authorization_code").containsEntry("code", "code-1")
                .containsEntry("client_secret", "uncava-secret").containsEntry("redirect_uri", CALLBACK.toString());
        assertThat(microsoft.authorizationOf("/graph/v1.0/me")).isEqualTo("Bearer eyJ0eXAi.access");
    }

    @Test
    @DisplayName("a first email is a draft then a send, and answers the immutable message id and conversation id")
    void aFirstEmailIsDraftedThenSent() throws Exception {
        String grantId = MailboxGrants.mintDirect("microsoft");
        when(mailboxTokens.accessToken(grantId)).thenReturn("access-for-yara");

        SentEmail sent = gateway.send(grantId, new OutgoingEmail("priya@client.example", "A CFO role", "<p>Hi</p>"));

        assertThat(sent.messageId()).isEqualTo("AAkALgAAAAAAHYQDEapmEc2byACqAC-EWg0A-draft");
        assertThat(sent.threadId()).isEqualTo("AAQkADAwATM0MDAAMS1iNTcwLWI2NTEtMDACLTAwCgAQAAmsg==");
        JsonNode draft = JSON.readTree(microsoft.bodyOf("POST /graph/v1.0/me/messages"));
        assertThat(draft.path("subject").asString("")).isEqualTo("A CFO role");
        assertThat(draft.path("body").path("contentType").asString("")).isEqualTo("HTML");
        assertThat(draft.path("toRecipients").get(0).path("emailAddress").path("address").asString(""))
                .isEqualTo("priya@client.example");
        assertThat(microsoft.requested()).containsSubsequence("POST /graph/v1.0/me/messages",
                "POST /graph/v1.0/me/messages/AAkALgAAAAAAHYQDEapmEc2byACqAC-EWg0A-draft/send");
        assertThat(microsoft.preferOf("/graph/v1.0/me/messages")).isEqualTo("IdType=\"ImmutableId\"");
    }

    @Test
    @DisplayName("a follow-up is a reply drafted on the last message, addressed to the executive, in the same conversation")
    void aFollowUpRepliesInTheConversation() throws Exception {
        String grantId = MailboxGrants.mintDirect("microsoft");
        when(mailboxTokens.accessToken(grantId)).thenReturn("access-for-yara");

        SentEmail sent = gateway.send(grantId, new OutgoingEmail("priya@client.example", "Re: A CFO role",
                "<p>Following up</p>", "AAkALgAAAAAAHYQDEapmEc2byACqAC-EWg0A-draft"));

        assertThat(sent.messageId()).isEqualTo("AAkALgAAAAAAHYQDEapmEc2byACqAC-EWg0A-reply");
        assertThat(sent.threadId()).isEqualTo("AAQkADAwATM0MDAAMS1iNTcwLWI2NTEtMDACLTAwCgAQAAmsg==");
        JsonNode reply = JSON.readTree(microsoft.bodyOf(
                "POST /graph/v1.0/me/messages/AAkALgAAAAAAHYQDEapmEc2byACqAC-EWg0A-draft/createReply"));
        assertThat(reply.path("message").path("toRecipients").get(0).path("emailAddress").path("address")
                .asString("")).isEqualTo("priya@client.example");
        assertThat(reply.path("message").path("subject").asString("")).isEqualTo("Re: A CFO role");
        JsonNode addressed = JSON.readTree(microsoft.bodyOf(
                "PATCH /graph/v1.0/me/messages/AAkALgAAAAAAHYQDEapmEc2byACqAC-EWg0A-reply"));
        assertThat(addressed.path("toRecipients")).hasSize(1);
        assertThat(addressed.path("toRecipients").get(0).path("emailAddress").path("address").asString(""))
                .isEqualTo("priya@client.example");
        assertThat(addressed.path("ccRecipients")).isEmpty();
        assertThat(addressed.path("bccRecipients")).isEmpty();
        assertThat(microsoft.requested()).containsSubsequence(
                "PATCH /graph/v1.0/me/messages/AAkALgAAAAAAHYQDEapmEc2byACqAC-EWg0A-reply",
                "POST /graph/v1.0/me/messages/AAkALgAAAAAAHYQDEapmEc2byACqAC-EWg0A-reply/send");
    }

    @Test
    @DisplayName("who wrote into a conversation is read by address only, from the send on, never drafts or Sent Items")
    void threadSendersAreAddressesOnly() {
        String grantId = MailboxGrants.mintDirect("microsoft");
        when(mailboxTokens.accessToken(grantId)).thenReturn("access-for-yara");

        List<String> writers = gateway.senderAddressesInThread(grantId,
                "AAQkADAwATM0MDAAMS1iNTcwLWI2NTEtMDACLTAwCgAQAAmsg==", Instant.parse("2026-10-01T00:00:00Z"));

        assertThat(writers).containsExactly("priya@client.example", "postmaster@client.example");
        String query = microsoft.lastQuery();
        assertThat(query).contains("$filter=conversationId eq 'AAQkADAwATM0MDAAMS1iNTcwLWI2NTEtMDACLTAwCgAQAAmsg=='")
                .contains("$select=from,receivedDateTime,isDraft,parentFolderId");
    }

    @Test
    @DisplayName("a refresh Microsoft refuses is final, and a refused app is never read as a dead grant")
    void refusalsAreToldApart() {
        microsoft.refreshAnswers(400, """
                {"error":"invalid_grant","error_description":"AADSTS700082: The refresh token has expired"}""");
        assertThatThrownBy(() -> tokenEndpoint.refresh(SHARED, "M.C123_refresh"))
                .isInstanceOf(ProviderGrantRefused.class);

        microsoft.refreshAnswers(401, """
                {"error":"invalid_client","error_description":"AADSTS7000215: Invalid client secret provided"}""");
        assertThatThrownBy(() -> tokenEndpoint.refresh(OWN, "M.C123_refresh"))
                .isInstanceOf(ProviderAppUnavailable.class);
        assertThat(microsoft.lastPath()).isEqualTo("/login/" + OWN.tenantId() + "/oauth2/v2.0/token");

        microsoft.refreshAnswers(429, "{}");
        assertThatThrownBy(() -> tokenEndpoint.refresh(SHARED, "M.C123_refresh"))
                .isInstanceOfSatisfying(VendorException.class,
                        failed -> assertThat(failed.getKind()).isEqualTo(VendorFailureKind.RATE_LIMITED));
    }

    @Test
    @DisplayName("a send Graph refuses deletes the unsent draft; one that may have gone leaves it alone")
    void aRefusedSendDiscardsTheDraft() {
        String grantId = MailboxGrants.mintDirect("microsoft");
        when(mailboxTokens.accessToken(grantId)).thenReturn("access-for-yara");
        OutgoingEmail email = new OutgoingEmail("priya@client.example", "A CFO role", "<p>Hi</p>");

        microsoft.sendAnswers(403);
        assertThatThrownBy(() -> gateway.send(grantId, email)).isInstanceOfSatisfying(VendorException.class,
                failed -> assertThat(failed.getKind()).isEqualTo(VendorFailureKind.CREDENTIALS));
        assertThat(microsoft.requested())
                .contains("DELETE /graph/v1.0/me/messages/AAkALgAAAAAAHYQDEapmEc2byACqAC-EWg0A-draft");

        microsoft.clearRequests();
        microsoft.sendAnswers(503);
        assertThatThrownBy(() -> gateway.send(grantId, email)).isInstanceOfSatisfying(VendorException.class,
                failed -> assertThat(failed.getKind()).isEqualTo(VendorFailureKind.UNAVAILABLE));
        assertThat(microsoft.requested()).noneMatch(request -> request.startsWith("DELETE"));
    }

    @Test
    @DisplayName("offered only where some app exists to connect through; per workspace, only where that one has one")
    void offeredOnlyWhereAnAppExists() {
        when(resolver.isAnyAppAt(IntegrationProvider.MICROSOFT)).thenReturn(false);
        assertThat(gateway.isOffered()).isFalse();
        when(resolver.isAnyAppAt(IntegrationProvider.MICROSOFT)).thenReturn(true);
        assertThat(gateway.isOffered()).isTrue();

        UUID withoutApp = UUID.randomUUID();
        when(resolver.resolve(withoutApp, IntegrationProvider.MICROSOFT)).thenReturn(Optional.empty());
        assertThat(gateway.isOfferedTo(WORKSPACE)).isTrue();
        assertThat(gateway.isOfferedTo(withoutApp)).isFalse();
    }

    @Test
    @DisplayName("a revoke forgets the token held in memory; Graph has nothing to call")
    void revokeForgetsTheToken() {
        String grantId = MailboxGrants.mintDirect("microsoft");

        gateway.revoke(new ReleasedGrant(grantId, null));

        verify(mailboxTokens).forget(grantId);
        assertThat(microsoft.requested()).isEmpty();
    }

    private static String decoded(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    /** Microsoft's answers, as its documentation records them, and every request it was sent. */
    private static final class RecordedMicrosoft {

        private final List<String> requested = new CopyOnWriteArrayList<>();
        private final Map<String, String> bodies = new ConcurrentHashMap<>();
        private final Map<String, String> authorizations = new ConcurrentHashMap<>();
        private final Map<String, String> prefers = new ConcurrentHashMap<>();
        private volatile String lastForm = "";
        private volatile String lastQuery = "";
        private volatile String lastPath = "";
        private volatile int refreshStatus = 200;
        private volatile int sendStatus = 202;
        private volatile String refreshBody;

        void answer(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String key = exchange.getRequestMethod() + " " + path;
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requested.add(key);
            bodies.put(key, body);
            lastPath = path;
            lastQuery = exchange.getRequestURI().getQuery() == null ? "" : exchange.getRequestURI().getQuery();
            authorizations.put(path, String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            prefers.putIfAbsent(path, String.valueOf(exchange.getRequestHeaders().getFirst("Prefer")));

            if (path.endsWith("/oauth2/v2.0/token")) {
                lastForm = body;
                if (body.contains("grant_type=refresh_token") && refreshBody != null) {
                    respond(exchange, refreshStatus, refreshBody);
                    return;
                }
                respond(exchange, 200, """
                        {"token_type":"Bearer","scope":"Mail.ReadWrite Mail.Send User.Read Calendars.ReadWrite",
                         "expires_in":4632,"ext_expires_in":4632,"access_token":"eyJ0eXAi.access",
                         "refresh_token":"M.C123_refresh"}""");
            } else if (path.equals("/graph/v1.0/me")) {
                respond(exchange, 200, """
                        {"@odata.context":"https://graph.microsoft.com/v1.0/$metadata#users(mail,userPrincipalName)/$entity",
                         "mail":"yara.haddad@meridian.example","userPrincipalName":"yhaddad@meridian.onmicrosoft.com"}""");
            } else if (key.equals("POST /graph/v1.0/me/messages")) {
                respond(exchange, 201, """
                        {"id":"AAkALgAAAAAAHYQDEapmEc2byACqAC-EWg0A-draft",
                         "conversationId":"AAQkADAwATM0MDAAMS1iNTcwLWI2NTEtMDACLTAwCgAQAAmsg==",
                         "internetMessageId":"<draft@meridian.example>","isDraft":true}""");
            } else if (path.endsWith("/createReply")) {
                respond(exchange, 201, """
                        {"id":"AAkALgAAAAAAHYQDEapmEc2byACqAC-EWg0A-reply",
                         "conversationId":"AAQkADAwATM0MDAAMS1iNTcwLWI2NTEtMDACLTAwCgAQAAmsg==","isDraft":true}""");
            } else if (path.endsWith("/send")) {
                respond(exchange, sendStatus, "");
            } else if (path.equals("/graph/v1.0/me/mailFolders/sentitems")) {
                respond(exchange, 200, "{\"id\":\"AAMkSentItems\"}");
            } else if (exchange.getRequestMethod().equals("PATCH") || exchange.getRequestMethod().equals("DELETE")) {
                respond(exchange, exchange.getRequestMethod().equals("PATCH") ? 200 : 204,
                        exchange.getRequestMethod().equals("PATCH") ? "{\"id\":\"patched\"}" : "");
            } else if (key.equals("GET /graph/v1.0/me/messages")) {
                respond(exchange, 200, """
                        {"value":[
                          {"receivedDateTime":"2026-09-20T09:00:00Z",
                           "from":{"emailAddress":{"name":"Old","address":"someone-earlier@client.example"}}},
                          {"receivedDateTime":"2026-10-01T09:00:00Z","isDraft":false,"parentFolderId":"AAMkSentItems",
                           "from":{"emailAddress":{"name":"Yara Haddad","address":"yhaddad@meridian.onmicrosoft.com"}}},
                          {"receivedDateTime":"2026-10-02T08:00:00Z","isDraft":true,"parentFolderId":"AAMkDrafts",
                           "from":{"emailAddress":{"name":"Yara Haddad","address":"yara.haddad@meridian.example"}}},
                          {"receivedDateTime":"2026-10-02T11:30:00Z",
                           "from":{"emailAddress":{"name":"Priya Raman","address":"priya@client.example"}}},
                          {"receivedDateTime":"2026-10-02T11:31:00Z",
                           "from":{"emailAddress":{"name":"Mail Delivery","address":"postmaster@client.example"}}}
                        ]}""");
            } else {
                respond(exchange, 404, "{\"error\":{\"code\":\"ErrorItemNotFound\"}}");
            }
        }

        void sendAnswers(int status) {
            this.sendStatus = status;
        }

        void clearRequests() {
            requested.clear();
        }

        void refreshAnswers(int status, String body) {
            this.refreshStatus = status;
            this.refreshBody = body;
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

        String authorizationOf(String path) {
            return authorizations.get(path);
        }

        String preferOf(String path) {
            return prefers.get(path);
        }

        String lastQuery() {
            return lastQuery;
        }

        String lastPath() {
            return lastPath;
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
