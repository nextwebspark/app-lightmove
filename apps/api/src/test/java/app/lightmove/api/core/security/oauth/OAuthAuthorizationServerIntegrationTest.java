package app.lightmove.api.core.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.core.security.token.Tokens;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/** The MCP authorization server end to end: the code flow, what it refuses, refresh rotation, and token separation. */
@IntegrationTest
class OAuthAuthorizationServerIntegrationTest extends OAuthFlowSupport {

    @Autowired JdbcTemplate db;
    @Autowired JwtEncoder sessionEncoder;

    @Test
    @DisplayName("an unauthenticated authorize request is sent to the SPA's consent screen with the request intact")
    void navigationLandsOnConsentScreen() throws Exception {
        String clientId = registerClient();
        MvcResult landed = mvc.perform(request(clientId, Tokens.generate(), "projects:read").asGet()).andReturn();

        assertThat(landed.getResponse().getStatus()).isEqualTo(302);
        String location = landed.getResponse().getRedirectedUrl();
        assertThat(location).startsWith(identity.issuer() + "/oauth/consent?");
        assertThat(queryParam(location, "client_id")).isEqualTo(clientId);
        assertThat(queryParam(location, "resource")).isNotNull();
    }

    @Test
    @DisplayName("the code flow yields an MCP token for the chosen workspace, audience-bound, with the granted scopes")
    void codeFlow() throws Exception {
        String user = adminOf(domain);
        String clientId = registerClient();
        String verifier = Tokens.generate();

        String state = storeRequest(request(clientId, verifier, "projects:read companies:read candidates.contacts:read"),
                user, workspaceOf(user));
        JsonNode pending = body(mvc.perform(get("/api/v1/oauth/consent").param("client_id", clientId)
                .param("state", state).header("Authorization", "Bearer " + user))
                .andExpect(status().isOk()).andReturn());
        assertThat(pending.get("requestedScopes").toString())
                .isEqualTo("[\"projects:read\",\"companies:read\",\"candidates.contacts:read\"]");
        assertThat(pending.get("workspaceId").asText()).isEqualTo(workspaceOf(user));

        MvcResult consented = consent(clientId, state, user, "projects:read", "companies:read");
        assertThat(consented.getResponse().getStatus()).isEqualTo(200);
        String redirect = body(consented).get("redirectUri").asText();
        assertThat(redirect).startsWith(REDIRECT + "?");
        assertThat(queryParam(redirect, "state")).isEqualTo(CLIENT_STATE);
        assertThat(queryParam(redirect, "iss")).isNotNull();

        MvcResult tokens = exchange(clientId, queryParam(redirect, "code"), verifier, identity.resourceUrl());
        assertThat(tokens.getResponse().getStatus()).as(tokens.getResponse().getContentAsString()).isEqualTo(200);
        JsonNode issued = body(tokens);
        assertThat(issued.get("refresh_token").asText()).isNotBlank();

        Jwt token = mcpTokens.decode(issued.get("access_token").asText());
        assertThat(token.getAudience()).containsExactly(identity.resourceUrl());
        assertThat(token.getSubject()).isEqualTo(sessionTokens.decode(user).getSubject());
        assertThat(token.getClaimAsString("wsId")).isEqualTo(workspaceOf(user));
        assertThat(token.getClaimAsStringList("scope")).containsExactlyInAnyOrder("projects:read", "companies:read");
        assertThat(token.getClaimAsString("client_id")).isEqualTo(clientId);
        assertThat(token.getClaimAsString("grant_id")).isNotBlank();
        assertThat(token.getClaims()).doesNotContainKeys("roles", "email");
        assertThat(token.getHeaders().get("kid").toString()).startsWith("mcp-");
    }

    @Test
    @DisplayName("no PKCE, a plain challenge, or a missing or foreign resource is refused back to the client")
    void malformedRequestsRefused() throws Exception {
        String clientId = registerClient();

        assertThat(errorOf(request(clientId, Tokens.generate(), "projects:read").with("code_challenge", null)))
                .isEqualTo("invalid_request");
        assertThat(errorOf(request(clientId, Tokens.generate(), "projects:read")
                .with("code_challenge_method", "plain"))).isEqualTo("invalid_request");
        assertThat(errorOf(request(clientId, Tokens.generate(), "projects:read").with("resource", null)))
                .isEqualTo("invalid_target");
        assertThat(errorOf(request(clientId, Tokens.generate(), "projects:read")
                .with("resource", "https://evil.example/mcp"))).isEqualTo("invalid_target");
    }

    @Test
    @DisplayName("an unknown client or redirect is never redirected to: the browser goes to the consent screen's error")
    void unknownClientNotRedirected() throws Exception {
        String clientId = registerClient();

        MvcResult unknownClient = mvc.perform(request("nobody", Tokens.generate(), "projects:read").asGet()).andReturn();
        assertThat(unknownClient.getResponse().getRedirectedUrl()).startsWith(identity.issuer() + "/oauth/consent?error=");

        MvcResult foreignRedirect = mvc.perform(request(clientId, Tokens.generate(), "projects:read")
                .with("redirect_uri", "https://evil.example/cb").asGet()).andReturn();
        assertThat(foreignRedirect.getResponse().getRedirectedUrl())
                .startsWith(identity.issuer() + "/oauth/consent?error=");
    }

    @Test
    @DisplayName("a pure client representative cannot connect their workspace, nor anyone a workspace they are not in")
    void ineligibleWorkspaceRefused() throws Exception {
        String admin = adminOf(domain);
        String representative = clientRepresentative(admin, "Rana Client", "rana@client-" + domain);
        String outsider = adminOf("other-" + domain);
        String clientId = registerClient();

        MvcResult pureClient = mvc.perform(request(clientId, Tokens.generate(), "projects:read")
                .with("workspace_id", workspaceOf(representative)).asPost(representative)).andReturn();
        assertThat(queryParam(body(pureClient).get("redirectUri").asText(), "error")).isEqualTo("access_denied");

        MvcResult stranger = mvc.perform(request(clientId, Tokens.generate(), "projects:read")
                .with("workspace_id", workspaceOf(admin)).asPost(outsider)).andReturn();
        assertThat(queryParam(body(stranger).get("redirectUri").asText(), "error")).isEqualTo("access_denied");
    }

    @Test
    @DisplayName("a scope repeated in the request is read once, never a 500")
    void repeatedScopeOnConsentScreen() throws Exception {
        String user = adminOf(domain);
        String clientId = registerClient();

        JsonNode context = body(mvc.perform(get("/api/v1/oauth/consent-context").param("client_id", clientId)
                        .param("scope", "projects:read projects:read")
                        .header("Authorization", "Bearer " + user))
                .andExpect(status().isOk()).andReturn());
        assertThat(context.get("requestedScopes").toString()).isEqualTo("[\"projects:read\"]");
    }

    @Test
    @DisplayName("consent cannot grant a scope the client never asked for, and denying grants nothing")
    void consentWithinRequest() throws Exception {
        String user = adminOf(domain);
        String clientId = registerClient();

        String state = storeRequest(request(clientId, Tokens.generate(), "projects:read"), user, workspaceOf(user));
        MvcResult widened = consent(clientId, state, user, "projects:read", "candidates.contacts:read");
        assertThat(queryParam(body(widened).get("redirectUri").asText(), "error")).isEqualTo("invalid_scope");

        String again = storeRequest(request(clientId, Tokens.generate(), "projects:read"), user, workspaceOf(user));
        MvcResult denied = consent(clientId, again, user);
        assertThat(queryParam(body(denied).get("redirectUri").asText(), "error")).isEqualTo("access_denied");
    }

    @Test
    @DisplayName("a code is spent only with its verifier and only on a token for the MCP resource")
    void tokenRequestBound() throws Exception {
        String user = adminOf(domain);
        String clientId = registerClient();
        String verifier = Tokens.generate();

        String code = authorize(clientId, verifier, user, "projects:read");
        assertThat(body(exchange(clientId, code, verifier, null)).get("error").asText()).isEqualTo("invalid_target");
        assertThat(body(exchange(clientId, code, Tokens.generate(), identity.resourceUrl())).get("error").asText())
                .isEqualTo("invalid_grant");
    }

    @Test
    @DisplayName("a refresh rotates the token; replaying the old one revokes the grant, the newer token included")
    void refreshRotatesAndReplayRevokes() throws Exception {
        String user = adminOf(domain);
        String clientId = registerClient();
        JsonNode first = connect(clientId, user, "projects:read");
        String grantId = mcpTokens.decode(first.get("access_token").asText()).getClaimAsString("grant_id");

        MvcResult rotated = refresh(clientId, first.get("refresh_token").asText());
        assertThat(rotated.getResponse().getStatus()).as(rotated.getResponse().getContentAsString()).isEqualTo(200);
        JsonNode second = body(rotated);
        assertThat(second.get("refresh_token").asText()).isNotEqualTo(first.get("refresh_token").asText());
        assertThat(mcpTokens.decode(second.get("access_token").asText()).getClaimAsString("wsId"))
                .isEqualTo(workspaceOf(user));
        assertThat(auditCount("OAUTH_TOKEN_REFRESHED", grantId)).isEqualTo(1);

        MvcResult replay = refresh(clientId, first.get("refresh_token").asText());
        assertThat(body(replay).get("error").asText()).isEqualTo("invalid_grant");
        assertThat(db.queryForObject("select count(*) from app_lm_oauth_authorization where id = ?::uuid",
                Integer.class, grantId)).isZero();
        assertThat(body(refresh(clientId, second.get("refresh_token").asText())).get("error").asText())
                .isEqualTo("invalid_grant");
        assertThat(db.queryForObject("""
                select metadata ->> 'reason' from app_lm_audit_event
                where event_type = 'OAUTH_GRANT_REVOKED' and target_id = ?""", String.class, grantId))
                .isEqualTo("REFRESH_REUSE");
    }

    @Test
    @DisplayName("no stored column holds a token as issued: only their hashes")
    void tokensStoredHashed() throws Exception {
        String user = adminOf(domain);
        String clientId = registerClient();
        JsonNode issued = connect(clientId, user, "projects:read");
        String grantId = mcpTokens.decode(issued.get("access_token").asText()).getClaimAsString("grant_id");

        Map<String, Object> row = db.queryForMap("select * from app_lm_oauth_authorization where id = ?::uuid",
                grantId);
        String everything = row.values().toString();
        assertThat(everything).doesNotContain(issued.get("access_token").asText())
                .doesNotContain(issued.get("refresh_token").asText());
        assertThat(row.get("refresh_token_hash")).isEqualTo(Tokens.hash(issued.get("refresh_token").asText()));
        assertThat(row.get("access_token_hash")).isEqualTo(Tokens.hash(issued.get("access_token").asText()));
    }

    @Test
    @DisplayName("an MCP token opens nothing under /api/v1, and a session token is no MCP token")
    void tokensDoNotCross() throws Exception {
        String user = adminOf(domain);
        String mcpToken = connect(registerClient(), user, "projects:read").get("access_token").asText();

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + mcpToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/workspace/api-keys").header("Authorization", "Bearer " + mcpToken))
                .andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> mcpTokens.decode(user)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("the session decoder refuses a token on its own key that carries an audience or another issuer")
    void sessionDecoderChecksIssuerAndAudience() throws Exception {
        String user = adminOf(domain);
        Jwt session = sessionTokens.decode(user);

        String withAudience = mint(session, "lightmove", List.of(identity.resourceUrl()));
        String otherIssuer = mint(session, "someone-else", null);
        String faithful = mint(session, "lightmove", null);

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + withAudience))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + otherIssuer))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + faithful))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("the metadata names only what this server does, and its JWKS only the MCP key")
    void metadata() throws Exception {
        JsonNode metadata = body(mvc.perform(get("/.well-known/oauth-authorization-server"))
                .andExpect(status().isOk()).andReturn());
        assertThat(metadata.get("issuer").asText()).isEqualTo(identity.issuer());
        assertThat(metadata.get("authorization_endpoint").asText()).endsWith("/api/v1/oauth/authorize");
        assertThat(metadata.get("token_endpoint").asText()).endsWith("/api/v1/oauth/token");
        assertThat(metadata.get("grant_types_supported").toString())
                .isEqualTo("[\"authorization_code\",\"refresh_token\"]");
        assertThat(metadata.get("token_endpoint_auth_methods_supported").toString()).isEqualTo("[\"none\"]");
        assertThat(metadata.get("code_challenge_methods_supported").toString()).isEqualTo("[\"S256\"]");
        assertThat(metadata.get("scopes_supported").toString()).contains("candidates.contacts:read");
        assertThat(metadata.get("authorization_response_iss_parameter_supported").asBoolean()).isTrue();
        assertThat(metadata.has("device_authorization_endpoint")).isFalse();
        assertThat(metadata.toString()).doesNotContain("/oauth2/");
        assertThat(metadata.has("revocation_endpoint")).isFalse();

        JsonNode keys = body(mvc.perform(get("/api/v1/oauth/jwks")).andExpect(status().isOk()).andReturn());
        assertThat(keys.get("keys")).hasSize(1);
        assertThat(keys.at("/keys/0/kid").asText()).startsWith("mcp-");
        assertThat(keys.at("/keys/0").has("d")).isFalse();
    }

    private String errorOf(AuthorizeRequest request) throws Exception {
        MvcResult refused = mvc.perform(request.asGet()).andReturn();
        String location = refused.getResponse().getRedirectedUrl();
        assertThat(location).as("refused to the client").startsWith(REDIRECT);
        assertThat(queryParam(location, "iss")).isNotNull();
        return queryParam(location, "error");
    }

    private String mint(Jwt session, String issuer, List<String> audience) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(session.getSubject())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claims(existing -> existing.putAll(Map.of(
                        "email", session.getClaimAsString("email"),
                        "emailVerified", true,
                        "wsId", session.getClaimAsString("wsId"))));
        if (audience != null) {
            claims.audience(audience);
        }
        return sessionEncoder.encode(JwtEncoderParameters.from(claims.build())).getTokenValue();
    }

    private String adminOf(String emailDomain) throws Exception {
        String alok = "alok@" + emailDomain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Keys Firm");
        return login(alok);
    }

    private int auditCount(String eventType, String grantId) {
        return db.queryForObject("""
                select count(*) from app_lm_audit_event
                where event_type = ? and target_type = 'oauth_grant' and target_id = ?""",
                Integer.class, eventType, grantId);
    }
}
