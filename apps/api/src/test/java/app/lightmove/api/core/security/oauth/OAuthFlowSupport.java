package app.lightmove.api.core.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.core.security.token.Tokens;
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
public abstract class OAuthFlowSupport extends FlowTestSupport {

    protected static final String AUTHORIZE = "/api/v1/oauth/authorize";
    protected static final String TOKEN = "/api/v1/oauth/token";
    protected static final String REGISTER = "/api/v1/oauth/register";
    protected static final String REVOKE = "/api/v1/oauth/revoke";
    protected static final String REDIRECT = "https://claude.ai/api/mcp/auth_callback";
    protected static final String CLIENT_STATE = "client-state-1";
    protected static final List<String> EVERY_SCOPE = List.of("projects:read", "companies:read", "candidates:read",
            "candidates.contacts:read", "candidates.compensation:read");

    @Autowired protected OAuthClientJpaRepository clients;
    @Autowired protected McpServerIdentity identity;
    @Autowired protected McpTokenDecoder mcpTokens;
    @Autowired protected JwtDecoder sessionTokens;

    /** A registered public client, fresh per test so no budget or grant is shared. */
    protected String registerClient() {
        String clientId = "test-client-" + UUID.randomUUID();
        clients.save(OAuthClient.registered(clientId, "Claude", "https://claude.ai", null, List.of(REDIRECT),
                EVERY_SCOPE, OAuthClientSource.SEEDED));
        return clientId;
    }

    protected static String challengeOf(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    protected String workspaceOf(String sessionToken) {
        return sessionTokens.decode(sessionToken).getClaimAsString("wsId");
    }

    /** The parameters an MCP client sends, every one correct; a test removes or replaces the one it is about. */
    protected AuthorizeRequest request(String clientId, String verifier, String scope) throws Exception {
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

    public record AuthorizeRequest(Map<String, String> parameters) {

        public AuthorizeRequest with(String name, String value) {
            if (value == null) {
                parameters.remove(name);
            } else {
                parameters.put(name, value);
            }
            return this;
        }

        public MockHttpServletRequestBuilder asGet() {
            MockHttpServletRequestBuilder builder = get(AUTHORIZE);
            parameters.forEach(builder::queryParam);
            return builder;
        }

        public MockHttpServletRequestBuilder asPost(String sessionToken) {
            MockHttpServletRequestBuilder builder = post(AUTHORIZE)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .header("Authorization", "Bearer " + sessionToken);
            parameters.forEach(builder::param);
            return builder;
        }
    }

    /** Step 3: the consent screen sends the request with the chosen workspace; answers the state to consent with. */
    protected String storeRequest(AuthorizeRequest request, String sessionToken, String workspaceId) throws Exception {
        MvcResult stored = mvc.perform(request.with("workspace_id", workspaceId).asPost(sessionToken)).andReturn();
        assertThat(stored.getResponse().getStatus()).as(stored.getResponse().getContentAsString()).isEqualTo(302);
        String location = stored.getResponse().getRedirectedUrl();
        // On the deployment's origin, never the request's host: the screen's fetch keeps its bearer only on that one.
        assertThat(location).startsWith(identity.issuer() + "/api/v1/oauth/consent?");
        return queryParam(location, "state");
    }

    /** Step 4: the consent itself, answered as JSON for the screen to follow. */
    protected MvcResult consent(String clientId, String state, String sessionToken, String... scopes) throws Exception {
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
    protected String authorize(String clientId, String verifier, String sessionToken, String... granted) throws Exception {
        String state = storeRequest(request(clientId, verifier, String.join(" ", EVERY_SCOPE)), sessionToken,
                workspaceOf(sessionToken));
        MvcResult consented = consent(clientId, state, sessionToken, granted);
        assertThat(consented.getResponse().getStatus()).as(consented.getResponse().getContentAsString())
                .isEqualTo(200);
        return queryParam(body(consented).get("redirectUri").asText(), "code");
    }

    protected MvcResult exchange(String clientId, String code, String verifier, String resource) throws Exception {
        return exchange(clientId, code, verifier, resource, REDIRECT);
    }

    protected MvcResult exchange(String clientId, String code, String verifier, String resource, String redirectUri)
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

    protected MvcResult refresh(String clientId, String refreshToken) throws Exception {
        return mvc.perform(post(TOKEN).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "refresh_token")
                .param("refresh_token", refreshToken)
                .param("client_id", clientId)
                .param("resource", identity.resourceUrl())).andReturn();
    }

    /** RFC 7591: what an MCP client posts to register itself, with nobody signed in. */
    protected MvcResult registerDynamically(String metadataJson) throws Exception {
        return mvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content(metadataJson)).andReturn();
    }

    /** RFC 7009 as a public client sends it: its id and the token, no secret. */
    protected MvcResult revoke(String clientId, String token) throws Exception {
        return mvc.perform(post(REVOKE).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("client_id", clientId)
                .param("token", token)).andReturn();
    }

    /** The whole flow, answering the token response. */
    protected JsonNode connect(String clientId, String sessionToken, String... granted) throws Exception {
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
    protected void assertRefusedOnOurPage(MvcResult refused) {
        assertThat(refused.getResponse().getRedirectedUrl()).as("refused on our page")
                .startsWith(identity.issuer() + "/oauth/consent?error=");
    }

    protected static String queryParam(String url, String name) {
        String raw = UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst(name);
        return raw == null ? null : UriUtils.decode(raw, StandardCharsets.UTF_8);
    }
}
