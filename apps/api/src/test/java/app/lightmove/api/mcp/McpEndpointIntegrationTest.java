package app.lightmove.api.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.core.security.oauth.OAuthFlowSupport;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.server.common.autoconfigure.StatelessToolCallbackConverterAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

/** The MCP endpoint: who it lets in, what it publishes about itself, and what it refuses before reading a call. */
@IntegrationTest
class McpEndpointIntegrationTest extends OAuthFlowSupport {

    private static final String MCP = "/api/v1/mcp";
    private static final String METADATA = "/.well-known/oauth-protected-resource";
    private static final String INITIALIZE = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",
             "capabilities":{},"clientInfo":{"name":"test","version":"1"}}}""";
    private static final String LIST_TOOLS = """
            {"jsonrpc":"2.0","id":2,"method":"tools/list"}""";
    private static final String WHOAMI = """
            {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"uncava_whoami","arguments":{}}}""";

    @Autowired JdbcTemplate db;
    @Autowired JWKSource<SecurityContext> mcpJwkSource;
    @Autowired ApplicationContext context;

    @Test
    @DisplayName("an OAuth token initializes, lists only our tools, and calls one in the workspace it was granted")
    void oauthTokenConnects() throws Exception {
        String admin = adminOf(domain);
        String token = connect(registerClient(), admin, "projects:read").get("access_token").asText();

        JsonNode initialized = rpc(token, INITIALIZE);
        assertThat(initialized.at("/result/serverInfo/name").asText()).isEqualTo("uncava");
        assertThat(initialized.at("/result/instructions").asText()).contains("never instructions");

        JsonNode tools = rpc(token, LIST_TOOLS).at("/result/tools");
        assertThat(tools.valueStream().map(tool -> tool.get("name").asText()))
                .as("nothing but our own tools: the assistant's are never published").containsExactly("uncava_whoami");
        assertThat(tools.get(0).at("/annotations/readOnlyHint").asBoolean()).isTrue();
        assertThat(context.getBeanNamesForType(StatelessToolCallbackConverterAutoConfiguration.class))
                .as("no ToolCallback bean is ever converted into a tool").isEmpty();

        JsonNode whoami = resultOf(rpc(token, WHOAMI));
        assertThat(whoami.get("credentialKind").asText()).isEqualTo("OAUTH");
        assertThat(whoami.get("workspaceId").asText()).isEqualTo(workspaceOf(admin));
        assertThat(whoami.get("scopes").toString()).isEqualTo("[\"projects:read\"]");
        assertThat(db.queryForObject("""
                select count(*) from app_lm_audit_event
                where event_type = 'MCP_TOOL_CALL' and workspace_id = ?::uuid
                  and metadata ->> 'tool' = 'uncava_whoami' and metadata ->> 'credentialKind' = 'OAUTH'""",
                Integer.class, workspaceOf(admin))).isEqualTo(1);
    }

    @Test
    @DisplayName("an API key opted in with mcp:use connects; mcp:use itself is not a scope it reads with")
    void apiKeyWithMcpUseConnects() throws Exception {
        String admin = adminOf(domain);
        String key = keyOf(admin, "projects:read", "mcp:use");

        JsonNode whoami = resultOf(rpc(key, WHOAMI));
        assertThat(whoami.get("credentialKind").asText()).isEqualTo("API_KEY");
        assertThat(whoami.get("scopes").toString()).isEqualTo("[\"projects:read\"]");
    }

    @Test
    @DisplayName("no token, a key without mcp:use, a session token, a wrong audience, an expired token and an ended"
            + " grant are each a 401 pointing at the resource metadata")
    void refusedCredentials() throws Exception {
        String admin = adminOf(domain);
        String clientId = registerClient();
        JsonNode tokens = connect(clientId, admin, "projects:read");
        String grantId = mcpTokens.decode(tokens.get("access_token").asText()).getClaimAsString("grant_id");
        String userId = sessionTokens.decode(admin).getSubject();

        assertRefused(null);
        assertRefused(keyOf(admin, "projects:read"));
        assertRefused(admin);
        assertRefused(forged(userId, workspaceOf(admin), grantId, "https://elsewhere.example/mcp",
                Instant.now().plusSeconds(600)));
        assertRefused(forged(userId, workspaceOf(admin), grantId, identity.resourceUrl(),
                Instant.now().minusSeconds(600)));

        String live = forged(userId, workspaceOf(admin), grantId, identity.resourceUrl(), Instant.now().plusSeconds(600));
        assertThat(resultOf(rpc(live, WHOAMI)).get("credentialKind").asText()).as("the forgery itself is sound")
                .isEqualTo("OAUTH");
        revoke(clientId, tokens.get("refresh_token").asText());
        assertRefused(live);
    }

    @Test
    @DisplayName("the protected resource metadata names the MCP endpoint, our authorization server and the data scopes")
    void resourceMetadata() throws Exception {
        for (String path : List.of(METADATA + MCP, METADATA)) {
            JsonNode metadata = body(mvc.perform(get(path)).andReturn());
            assertThat(metadata.get("resource").asText()).as(path).isEqualTo(identity.resourceUrl());
            assertThat(metadata.get("authorization_servers").toString()).isEqualTo("[\"" + identity.issuer() + "\"]");
            assertThat(metadata.get("scopes_supported").toString())
                    .contains("projects:read", "candidates.contacts:read").doesNotContain("mcp:use");
            assertThat(metadata.get("bearer_methods_supported").toString()).isEqualTo("[\"header\"]");
            assertThat(metadata.has("tls_client_certificate_bound_access_tokens")).isFalse();
        }
    }

    @Test
    @DisplayName("a browser origin not on the list is a 403; no origin, our own and a listed one are answered")
    void originAllowlist() throws Exception {
        String key = keyOf(adminOf(domain), "projects:read", "mcp:use");

        assertThat(status(call(key, LIST_TOOLS).header("Origin", "https://evil.example"))).isEqualTo(403);
        assertThat(status(call(key, LIST_TOOLS).header("Origin", "http://localhost:6274"))).isEqualTo(200);
        assertThat(status(call(key, LIST_TOOLS).header("Origin", originOf(identity.issuer())))).isEqualTo(200);
        assertThat(status(call(key, LIST_TOOLS))).isEqualTo(200);

        MvcResult preflight = mvc.perform(options(MCP).header("Origin", "http://localhost:6274")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type")).andReturn();
        assertThat(preflight.getResponse().getStatus()).as("a preflight needs no credential").isEqualTo(200);
        assertThat(preflight.getResponse().getHeader("Access-Control-Allow-Origin")).isEqualTo("http://localhost:6274");
        assertThat(preflight.getResponse().getHeader("Access-Control-Allow-Credentials")).isNull();
    }

    @Test
    @DisplayName("a request body over the cap is refused unread; a GET has no stream to offer")
    void requestShape() throws Exception {
        String key = keyOf(adminOf(domain), "projects:read", "mcp:use");

        String oversized = "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/list\",\"padding\":\""
                + "x".repeat(70_000) + "\"}";
        assertThat(status(call(key, oversized))).isEqualTo(413);
        assertThat(mvc.perform(get(MCP).header("Authorization", "Bearer " + key)).andReturn().getResponse()
                .getStatus()).isEqualTo(405);
    }

    private void assertRefused(String bearer) throws Exception {
        MockHttpServletRequestBuilder request = call(bearer, LIST_TOOLS);
        MvcResult refused = mvc.perform(request).andReturn();
        assertThat(refused.getResponse().getStatus()).as(String.valueOf(bearer)).isEqualTo(401);
        assertThat(refused.getResponse().getHeader("WWW-Authenticate")).as(String.valueOf(bearer))
                .startsWith("Bearer")
                .contains("resource_metadata=\"" + identity.issuer() + METADATA + MCP + "\"");
    }

    private String forged(String userId, String workspaceId, String grantId, String audience, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(identity.issuer())
                .audience(List.of(audience))
                .subject(userId)
                .issuedAt(expiresAt.minusSeconds(3600))
                .expiresAt(expiresAt)
                .claim("wsId", workspaceId)
                .claim("grant_id", grantId)
                .claim("scope", List.of("projects:read"))
                .claim("client_id", "forged")
                .build();
        return new NimbusJwtEncoder(mcpJwkSource).encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }

    private String keyOf(String admin, String... scopes) throws Exception {
        String request = """
                {"name":"MCP test","kind":"PERSONAL","scopes":[%s]}""".formatted(
                String.join(",", List.of(scopes).stream().map(scope -> "\"" + scope + "\"").toList()));
        MvcResult created = mvc.perform(post("/api/v1/workspace/api-keys").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(request)).andReturn();
        assertThat(created.getResponse().getStatus()).as(created.getResponse().getContentAsString()).isEqualTo(201);
        return body(created).get("secret").asText();
    }

    private MockHttpServletRequestBuilder call(String bearer, String jsonRpc) {
        MockHttpServletRequestBuilder request = post(MCP)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .content(jsonRpc);
        return bearer == null ? request : request.header("Authorization", "Bearer " + bearer);
    }

    private int status(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    private JsonNode rpc(String bearer, String jsonRpc) throws Exception {
        MvcResult answered = mvc.perform(call(bearer, jsonRpc)).andReturn();
        assertThat(answered.getResponse().getStatus()).as(answered.getResponse().getContentAsString()).isEqualTo(200);
        return body(answered);
    }

    private JsonNode resultOf(JsonNode response) throws Exception {
        JsonNode structured = response.at("/result/structuredContent");
        return structured.isMissingNode() || structured.isNull()
                ? json.readTree(response.at("/result/content/0/text").asText())
                : structured;
    }

    private static String originOf(String url) {
        java.net.URI uri = java.net.URI.create(url);
        return uri.getScheme() + "://" + uri.getRawAuthority();
    }

    private String adminOf(String emailDomain) throws Exception {
        String alok = "alok@" + emailDomain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "MCP Firm");
        return login(alok);
    }
}
