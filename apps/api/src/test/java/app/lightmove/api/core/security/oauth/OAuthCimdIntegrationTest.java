package app.lightmove.api.core.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.StubClientMetadataFetcher;
import app.lightmove.api.core.security.token.Tokens;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * Client id metadata documents: a client whose id is the https URL of its own metadata connects with no registration.
 * Documents are served by {@link StubClientMetadataFetcher}; the transport's guards are tested on their own.
 */
@IntegrationTest
class OAuthCimdIntegrationTest extends OAuthFlowSupport {

    @Autowired StubClientMetadataFetcher documents;
    @Autowired JdbcTemplate db;

    @Test
    @DisplayName("a metadata document's client completes the flow with no registration, and is shown as verified")
    void documentClientConnects() throws Exception {
        String clientId = documentAt("claude.ai");
        documents.serve(clientId, documentFor(clientId), Duration.ofHours(1));
        String user = adminOf(domain);
        assertThat(mvc.perform(request(clientId, Tokens.generate(), "projects:read").asGet()).andReturn()
                .getResponse().getRedirectedUrl()).as("on to sign-in").contains("/oauth/consent?")
                .doesNotContain("error=");

        JsonNode context = body(mvc.perform(get("/api/v1/oauth/consent-context").param("client_id", clientId)
                .param("scope", "projects:read")
                .header("Authorization", "Bearer " + user)).andReturn());
        assertThat(context.get("clientName").asText()).isEqualTo("Claude");
        assertThat(context.get("clientKind").asText()).isEqualTo("CIMD");
        assertThat(context.get("clientHost").asText()).isEqualTo("claude.ai");
        assertThat(context.get("verified").asBoolean()).isTrue();

        JsonNode tokens = connect(clientId, user, "projects:read");
        assertThat(mcpTokens.decode(tokens.get("access_token").asText()).getClaimAsString("client_id"))
                .isEqualTo(clientId);
        assertThat(clients.findByClientId(clientId).orElseThrow().getSource()).isEqualTo(OAuthClientSource.CIMD);
        assertThat(documents.fetched()).as("one fetch, then the copy").filteredOn(clientId::equals).hasSize(1);
    }

    @Test
    @DisplayName("a document on a host nobody listed is still usable, but not verified")
    void unlistedHostUnverified() throws Exception {
        String clientId = documentAt("tools.example.org");
        documents.serve(clientId, documentFor(clientId), Duration.ofHours(1));
        mvc.perform(request(clientId, Tokens.generate(), "projects:read").asGet());

        JsonNode context = body(mvc.perform(get("/api/v1/oauth/consent-context").param("client_id", clientId)
                .header("Authorization", "Bearer " + adminOf(domain))).andReturn());
        assertThat(context.get("verified").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("the consent screen's read never fetches a document the authorize request has not already loaded")
    void consentContextFetchesNothingNew() throws Exception {
        String clientId = documentAt("claude.ai");
        documents.serve(clientId, documentFor(clientId), Duration.ofHours(1));

        mvc.perform(get("/api/v1/oauth/consent-context").param("client_id", clientId)
                        .header("Authorization", "Bearer " + adminOf(domain)))
                .andExpect(status().isNotFound());
        assertThat(documents.fetched()).doesNotContain(clientId);
    }

    @Test
    @DisplayName("a document whose client_id names another URL is refused, and nothing is stored")
    void mismatchedClientIdRefused() throws Exception {
        String clientId = documentAt("claude.ai");
        documents.serve(clientId, documentFor("https://claude.ai/someone-else"), Duration.ofHours(1));

        assertRefusedOnOurPage(mvc.perform(request(clientId, Tokens.generate(), "projects:read").asGet()).andReturn());
        assertThat(clients.findByClientId(clientId)).isEmpty();
    }

    @Test
    @DisplayName("a redirect the document does not list is refused before anyone is asked to sign in")
    void unlistedRedirectRefused() throws Exception {
        String clientId = documentAt("claude.ai");
        documents.serve(clientId, documentFor(clientId), Duration.ofHours(1));

        assertRefusedOnOurPage(mvc.perform(request(clientId, Tokens.generate(), "projects:read")
                .with("redirect_uri", "https://attacker.example/callback").asGet()).andReturn());
    }

    @Test
    @DisplayName("an expired copy is fetched again and picks up the client's changes")
    void expiredCopyRefetched() throws Exception {
        String clientId = documentAt("claude.ai");
        documents.serve(clientId, documentFor(clientId), Duration.ofHours(1));
        assertThat(mvc.perform(request(clientId, Tokens.generate(), "projects:read").asGet()).andReturn()
                .getResponse().getStatus()).isEqualTo(302);

        expire(clientId, Duration.ofMinutes(1));
        documents.serve(clientId, documentFor(clientId).replace("\"Claude\"", "\"Claude (new)\""), Duration.ofHours(1));
        assertThat(mvc.perform(request(clientId, Tokens.generate(), "projects:read").asGet()).andReturn()
                .getResponse().getStatus()).isEqualTo(302);

        assertThat(documents.fetched()).filteredOn(clientId::equals).hasSize(2);
        assertThat(clients.findByClientId(clientId).orElseThrow().getClientName()).isEqualTo("Claude (new)");
    }

    @Test
    @DisplayName("while the client's host is down, a recent copy keeps serving; an old one does not")
    void staleCopyServesThroughAnOutage() throws Exception {
        String clientId = documentAt("claude.ai");
        documents.serve(clientId, documentFor(clientId), Duration.ofHours(1));
        String user = adminOf(domain);
        JsonNode tokens = connect(clientId, user, "projects:read");

        expire(clientId, Duration.ofHours(2));
        documents.takeDown(clientId);
        MvcResult refreshed = refresh(clientId, tokens.get("refresh_token").asText());
        assertThat(refreshed.getResponse().getStatus()).as(refreshed.getResponse().getContentAsString())
                .isEqualTo(200);

        expire(clientId, Duration.ofHours(25));
        MvcResult refused = refresh(clientId, body(refreshed).get("refresh_token").asText());
        assertThat(body(refused).get("error").asText()).isEqualTo("invalid_client");
    }

    @Test
    @DisplayName("a client id that is an https URL but not a usable document address is refused without a fetch")
    void unusableDocumentUrlNotFetched() throws Exception {
        String[] unusable = {"https://claude.ai", "https://claude.ai/", "https://claude.ai/a/../b",
                "https://user@claude.ai/doc"};
        for (String clientId : unusable) {
            assertRefusedOnOurPage(mvc.perform(request(clientId, Tokens.generate(), "projects:read").asGet())
                    .andReturn());
        }
        assertThat(documents.fetched()).doesNotContain(unusable);
    }

    /** Moves the stored copy back in time, as if fetched {@code age} ago and expired a moment ago. */
    private void expire(String clientId, Duration age) {
        db.update("""
                update app_lm_oauth_client
                set metadata_fetched_at = now() - make_interval(secs => ?),
                    metadata_expires_at = now() - interval '1 second'
                where client_id = ?""", age.toSeconds(), clientId);
    }

    private static String documentAt(String host) {
        return "https://" + host + "/oauth/mcp-client-metadata-" + UUID.randomUUID() + ".json";
    }

    private static String documentFor(String clientId) {
        return """
                {"client_id":"%s","client_name":"Claude","client_uri":"https://claude.ai",
                 "logo_uri":"https://claude.ai/logo.png","redirect_uris":["%s"],
                 "grant_types":["authorization_code","refresh_token"],"response_types":["code"],
                 "token_endpoint_auth_method":"none"}""".formatted(clientId, REDIRECT);
    }

    private String adminOf(String emailDomain) throws Exception {
        String alok = "alok@" + emailDomain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Keys Firm");
        return login(alok);
    }
}
