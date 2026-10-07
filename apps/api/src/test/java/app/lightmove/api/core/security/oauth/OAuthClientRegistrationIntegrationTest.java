package app.lightmove.api.core.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.core.security.token.Tokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/** Dynamic client registration (RFC 7591): who may register, as what, and that the client it makes can connect. */
@IntegrationTest
class OAuthClientRegistrationIntegrationTest extends OAuthFlowSupport {

    private static final String LOOPBACK_REDIRECT = "http://localhost:4000/callback";

    @Autowired JdbcTemplate db;
    @Autowired OAuthGrantPurge purge;

    @Test
    @DisplayName("a client registers with nobody signed in, gets no secret, and completes the flow")
    void registersAndConnects() throws Exception {
        MvcResult registered = registerDynamically("""
                {"client_name":"Claude","redirect_uris":["%s"],"token_endpoint_auth_method":"none",
                 "grant_types":["authorization_code","refresh_token"],"response_types":["code"],
                 "logo_uri":"https://claude.ai/logo.png","client_uri":"http://claude.ai"}""".formatted(REDIRECT));
        assertThat(registered.getResponse().getStatus()).as(registered.getResponse().getContentAsString())
                .isEqualTo(201);
        JsonNode client = body(registered);
        String clientId = client.get("client_id").asText();
        assertThat(client.has("client_secret")).isFalse();
        assertThat(client.get("token_endpoint_auth_method").asText()).isEqualTo("none");

        OAuthClient stored = clients.findByClientId(clientId).orElseThrow();
        assertThat(stored.getSource()).isEqualTo(OAuthClientSource.DCR);
        assertThat(stored.getScopes()).containsExactlyInAnyOrderElementsOf(EVERY_SCOPE);
        assertThat(stored.getLogoUri()).isEqualTo("https://claude.ai/logo.png");
        assertThat(stored.getClientUri()).as("an http home page is dropped").isNull();
        assertThat(db.queryForObject("select count(*) from app_lm_audit_event "
                + "where event_type = 'OAUTH_CLIENT_REGISTERED' and target_id = ?", Integer.class,
                stored.getId().toString())).isEqualTo(1);

        String user = adminOf(domain);
        JsonNode tokens = connect(clientId, user, "projects:read");
        assertThat(mcpTokens.decode(tokens.get("access_token").asText()).getClaimAsString("client_id"))
                .isEqualTo(clientId);

        JsonNode context = body(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/v1/oauth/consent-context").param("client_id", clientId)
                .header("Authorization", "Bearer " + user)).andReturn());
        assertThat(context.get("clientKind").asText()).isEqualTo("DCR");
        assertThat(context.get("verified").asBoolean()).as("a registration may call itself Claude").isFalse();
    }

    @Test
    @DisplayName("an unknown scope is dropped; a known one bounds what the client may ask")
    void scopesNarrowed() throws Exception {
        String clientId = body(registerDynamically("""
                {"client_name":"Reader","redirect_uris":["%s"],"scope":"projects:read openid admin"}"""
                .formatted(REDIRECT))).get("client_id").asText();
        assertThat(clients.findByClientId(clientId).orElseThrow().getScopes()).containsExactly("projects:read");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"redirect_uris\":[\"https://app.example.com/cb\"],\"token_endpoint_auth_method\":\"client_secret_basic\"}",
            "{\"redirect_uris\":[\"https://app.example.com/cb\"],\"grant_types\":[\"client_credentials\"]}",
            "{\"redirect_uris\":[\"https://app.example.com/cb\"],\"response_types\":[\"token\"]}",
            "{\"redirect_uris\":[\"http://app.example.com/cb\"]}",
            "{\"redirect_uris\":[\"cursor://anysphere.cursor-retrieval/oauth/callback\"]}",
            "{\"redirect_uris\":[\"https://app.example.com/cb#fragment\"]}",
            "{\"redirect_uris\":[\"https://*.example.com/cb\"]}",
            "{\"redirect_uris\":[\"https://user@app.example.com/cb\"]}",
            "{\"redirect_uris\":[]}",
            "{\"client_name\":\"No redirects\"}",
            "{\"redirect_uris\":[\"https://app.example.com/cb\"],\"jwks_uri\":\"https://app.example.com/jwks\"}"
    })
    @DisplayName("a registration outside what a public MCP client needs is refused, and nothing is stored")
    void refusedRegistrations(String metadata) throws Exception {
        long before = clients.count();
        MvcResult refused = registerDynamically(metadata);
        assertThat(refused.getResponse().getStatus()).as(refused.getResponse().getContentAsString()).isEqualTo(400);
        assertThat(body(refused).get("error").asText())
                .isIn("invalid_client_metadata", "invalid_redirect_uri", "invalid_request");
        assertThat(clients.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("a loopback redirect may come back on any port, localhost included, but on no other path or host")
    void loopbackPortMayChange() throws Exception {
        String clientId = body(registerDynamically("""
                {"client_name":"Claude Code","redirect_uris":["%s"]}""".formatted(LOOPBACK_REDIRECT)))
                .get("client_id").asText();
        String user = adminOf(domain);
        String verifier = Tokens.generate();
        String otherPort = "http://localhost:53817/callback";

        String state = storeRequest(request(clientId, verifier, "projects:read").with("redirect_uri", otherPort),
                user, workspaceOf(user));
        MvcResult consented = consent(clientId, state, user, "projects:read");
        String redirect = body(consented).get("redirectUri").asText();
        assertThat(redirect).startsWith(otherPort + "?");
        MvcResult tokens = exchange(clientId, queryParam(redirect, "code"), verifier, identity.resourceUrl(), otherPort);
        assertThat(tokens.getResponse().getStatus()).as(tokens.getResponse().getContentAsString()).isEqualTo(200);

        for (String elsewhere : new String[] {"http://localhost:53817/other", "http://evil.example:4000/callback",
                "https://localhost:4000/callback"}) {
            assertRefusedOnOurPage(mvc.perform(request(clientId, Tokens.generate(), "projects:read")
                    .with("redirect_uri", elsewhere).asGet()).andReturn());
        }
    }

    @Test
    @DisplayName("a redirect the client did not register is refused before anyone is asked to sign in")
    void mismatchedRedirectRefusedFirst() throws Exception {
        String clientId = body(registerDynamically("""
                {"client_name":"App","redirect_uris":["%s"]}""".formatted(REDIRECT))).get("client_id").asText();

        assertRefusedOnOurPage(mvc.perform(request(clientId, Tokens.generate(), "projects:read")
                .with("redirect_uri", "https://attacker.example/callback").asGet()).andReturn());
    }

    @Test
    @DisplayName("the purge removes a registration nobody connected and keeps one with a grant")
    void unusedRegistrationsPruned() throws Exception {
        String unused = body(registerDynamically("""
                {"client_name":"Unused","redirect_uris":["%s"]}""".formatted(REDIRECT))).get("client_id").asText();
        String used = body(registerDynamically("""
                {"client_name":"Used","redirect_uris":["%s"]}""".formatted(REDIRECT))).get("client_id").asText();
        connect(used, adminOf(domain), "projects:read");

        purge.purgeAt(java.time.Instant.now().plus(java.time.Duration.ofDays(29)));
        assertThat(clients.findByClientId(unused)).isPresent();

        db.update("""
                update app_lm_oauth_client set created_at = now() - interval '40 days',
                                               last_authorized_at = now() - interval '40 days'
                where client_id in (?, ?)""", unused, used);
        purge.purgeAt(java.time.Instant.now());
        assertThat(clients.findByClientId(unused)).isEmpty();
        assertThat(clients.findByClientId(used)).as("its grant is still live").isPresent();
    }

    private String adminOf(String emailDomain) throws Exception {
        String alok = "alok@" + emailDomain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Keys Firm");
        return login(alok);
    }
}
