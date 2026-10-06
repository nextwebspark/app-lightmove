package app.lightmove.api.core.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.core.security.token.Tokens;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.JsonNode;

/** Drives the authorization server the way an MCP client and the consent screen do, one step at a time. */
abstract class OAuthFlowSupport extends FlowTestSupport {

    static final String AUTHORIZE = "/api/v1/oauth/authorize";
    static final String TOKEN = "/api/v1/oauth/token";
    static final String REGISTER = "/api/v1/oauth/register";
    static final String REVOKE = "/api/v1/oauth/revoke";
    static final String REDIRECT = "https://claude.ai/api/mcp/auth_callback";
    static final String CLIENT_STATE = "client-state-1";
    static final List<String> EVERY_SCOPE = List.of("projects:read", "companies:read", "candidates:read",
            "candidates.contacts:read", "candidates.compensation:read");

    @Autowired OAuthClientJpaRepository clients;
    @Autowired McpServerIdentity identity;
    @Autowired McpTokenDecoder mcpTokens;
    @Autowired JwtDecoder sessionTokens;

    /** A registered public client, fresh per test so no budget or grant is shared. */
    String registerClient() {
        String clientId = "test-client-" + UUID.randomUUID();
        clients.save(OAuthClient.registered(clientId, "Claude", "https://claude.ai", null, List.of(REDIRECT),
                EVERY_SCOPE, OAuthClientSource.SEEDED));
        return clientId;
    }

    static String challengeOf(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    String workspaceOf(String sessionToken) {
        return sessionTokens.decode(sessionToken).getClaimAsString("wsId");
    }

    /** The parameters an MCP client sends, every one correct; a test removes or replaces the one it is about. */
    AuthorizeRequest request(String clientId, String verifier, String scope) throws Exception {
        return new AuthorizeRequest(new java.util.LinkedHashMap<>(Map.of(
                "response_type", "code",
                "client_id", clientId,
                "redirect_uri", REDIRECT,
                "scope", scope,
                "state", CLIENT_STATE,
                "code_challenge", challengeOf(verifier),
                "code_challenge_method", "S256",
                "resource", identity.resourceUrl())));
    }

    record AuthorizeRequest(Map<String, String> parameters) {

        AuthorizeRequest with(String name, String value) {
            if (value == null) {
                parameters.remove(name);
            } else {
                parameters.put(name, value);
            }
            return this;
        }

        MockHttpServletRequestBuilder asGet() {
            MockHttpServletRequestBuilder builder = get(AUTHORIZE);
            parameters.forEach(builder::queryParam);
            return builder;
        }

        MockHttpServletRequestBuilder asPost(String sessionToken) {
            MockHttpServletRequestBuilder builder = post(AUTHORIZE)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .header("Authorization", "Bearer " + sessionToken);
            parameters.forEach(builder::param);
            return builder;
        }
    }

    /** Step 3: the consent screen sends the request with the chosen workspace; answers the state to consent with. */
    String storeRequest(AuthorizeRequest request, String sessionToken, String workspaceId) throws Exception {
        MvcResult stored = mvc.perform(request.with("workspace_id", workspaceId).asPost(sessionToken)).andReturn();
        assertThat(stored.getResponse().getStatus()).as(stored.getResponse().getContentAsString()).isEqualTo(302);
        String location = stored.getResponse().getRedirectedUrl();
        assertThat(URI.create(location).getPath()).isEqualTo("/api/v1/oauth/consent");
        return queryParam(location, "state");
    }

    /** Step 4: the consent itself, answered as JSON for the screen to follow. */
    MvcResult consent(String clientId, String state, String sessionToken, String... scopes) throws Exception {
        MockHttpServletRequestBuilder builder = post(AUTHORIZE)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Authorization", "Bearer " + sessionToken)
                .param("client_id", clientId)
                .param("state", state);
        for (String scope : scopes) {
            builder.param("scope", scope);
        }
        return mvc.perform(builder).andReturn();
    }

    /** Steps 3 and 4 in a row, answering the code. */
    String authorize(String clientId, String verifier, String sessionToken, String... granted) throws Exception {
        String state = storeRequest(request(clientId, verifier, String.join(" ", EVERY_SCOPE)), sessionToken,
                workspaceOf(sessionToken));
        MvcResult consented = consent(clientId, state, sessionToken, granted);
        assertThat(consented.getResponse().getStatus()).as(consented.getResponse().getContentAsString())
                .isEqualTo(200);
        return queryParam(body(consented).get("redirectUri").asText(), "code");
    }

    MvcResult exchange(String clientId, String code, String verifier, String resource) throws Exception {
        return exchange(clientId, code, verifier, resource, REDIRECT);
    }

    MvcResult exchange(String clientId, String code, String verifier, String resource, String redirectUri)
            throws Exception {
        MockHttpServletRequestBuilder builder = post(TOKEN).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code")
                .param("code", code)
                .param("redirect_uri", redirectUri)
                .param("client_id", clientId)
                .param("code_verifier", verifier);
        if (resource != null) {
            builder.param("resource", resource);
        }
        return mvc.perform(builder).andReturn();
    }

    MvcResult refresh(String clientId, String refreshToken) throws Exception {
        return mvc.perform(post(TOKEN).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "refresh_token")
                .param("refresh_token", refreshToken)
                .param("client_id", clientId)
                .param("resource", identity.resourceUrl())).andReturn();
    }

    /** RFC 7591: what an MCP client posts to register itself, with nobody signed in. */
    MvcResult registerDynamically(String metadataJson) throws Exception {
        return mvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content(metadataJson)).andReturn();
    }

    /** RFC 7009 as a public client sends it: its id and the token, no secret. */
    MvcResult revoke(String clientId, String token) throws Exception {
        return mvc.perform(post(REVOKE).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("client_id", clientId)
                .param("token", token)).andReturn();
    }

    /** The whole flow, answering the token response. */
    JsonNode connect(String clientId, String sessionToken, String... granted) throws Exception {
        String verifier = Tokens.generate();
        MvcResult tokens = exchange(clientId, authorize(clientId, verifier, sessionToken, granted), verifier,
                identity.resourceUrl());
        assertThat(tokens.getResponse().getStatus()).as(tokens.getResponse().getContentAsString()).isEqualTo(200);
        return body(tokens);
    }

    /**
     * A request that names no client or redirect we can trust is shown on our own consent page as an error — never
     * sent to the address it named, and never on to sign-in.
     */
    void assertRefusedOnOurPage(MvcResult refused) {
        assertThat(refused.getResponse().getRedirectedUrl()).as("refused on our page")
                .startsWith(identity.issuer() + "/oauth/consent?error=");
    }

    static String queryParam(String url, String name) {
        String raw = UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst(name);
        return raw == null ? null : UriUtils.decode(raw, StandardCharsets.UTF_8);
    }
}
