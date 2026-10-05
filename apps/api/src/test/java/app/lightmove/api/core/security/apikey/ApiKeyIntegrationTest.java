package app.lightmove.api.core.security.apikey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.core.security.token.Tokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/** Settings → API keys: who may make which key, who sees and revokes it, and that its secret is shown once. */
@IntegrationTest
class ApiKeyIntegrationTest extends FlowTestSupport {

    private static final String KEYS = "/api/v1/workspace/api-keys";

    @Autowired JdbcTemplate db;

    @Test
    @DisplayName("a member's personal key is shown once, stored only as its hash, and listed without its secret")
    void secretShownOnce() throws Exception {
        String admin = adminOfNewWorkspace();
        String member = memberOf(admin, "sara");

        MvcResult created = create(member, """
                {"name":"Power BI","scopes":["companies:read","projects:read","companies:read"],"expiresInDays":30}""");
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        String secret = body(created).get("secret").asText();
        String keyId = body(created).at("/key/id").asText();

        assertThat(secret).startsWith("uncava_pat_");
        assertThat(ApiKeySecrets.isWellFormed(secret)).isTrue();
        assertThat(body(created).at("/key/kind").asText()).isEqualTo("PERSONAL");
        assertThat(body(created).at("/key/status").asText()).isEqualTo("ACTIVE");
        assertThat(body(created).at("/key/scopes").toString()).isEqualTo("[\"projects:read\",\"companies:read\"]");
        assertThat(db.queryForObject("select token_hash from app_lm_api_key where id = ?::uuid", String.class, keyId))
                .isEqualTo(Tokens.hash(secret));

        String listed = mvc.perform(get(KEYS).header("Authorization", "Bearer " + member))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(listed).contains(keyId).doesNotContain(secret);
        assertThat(auditCount("API_KEY_CREATED", keyId)).isEqualTo(1);
    }

    @Test
    @DisplayName("only WORKSPACE_MANAGE makes a workspace key or lists every key; a pure client makes none")
    void whoMakesWhichKey() throws Exception {
        String admin = adminOfNewWorkspace();
        String member = memberOf(admin, "sara");
        String client = clientRepresentative(admin, "Rana Client", "rana@client-" + domain);

        assertThat(create(member, serviceKey()).getResponse().getStatus()).isEqualTo(403);
        mvc.perform(get(KEYS).param("all", "true").header("Authorization", "Bearer " + member))
                .andExpect(status().isForbidden());
        assertThat(create(client, personalKey()).getResponse().getStatus()).isEqualTo(403);

        String memberKey = body(create(member, personalKey())).at("/key/id").asText();
        MvcResult service = create(admin, serviceKey());
        assertThat(service.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(service).get("secret").asText()).startsWith("uncava_svc_");

        JsonNode everyKey = body(mvc.perform(get(KEYS).param("all", "true").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn());
        assertThat(everyKey.valueStream().map(key -> key.get("id").asText()))
                .contains(memberKey, body(service).at("/key/id").asText());
        assertThat(body(mvc.perform(get(KEYS).header("Authorization", "Bearer " + admin)).andReturn())).isEmpty();
    }

    @Test
    @DisplayName("a member revokes only their own key; an admin revokes anyone's; another workspace's key is not found")
    void whoRevokes() throws Exception {
        String admin = adminOfNewWorkspace();
        String member = memberOf(admin, "sara");
        String memberKey = body(create(member, personalKey())).at("/key/id").asText();
        String serviceKey = body(create(admin, serviceKey())).at("/key/id").asText();

        assertThat(codeOf(mvc.perform(delete(KEYS + "/" + serviceKey).header("Authorization", "Bearer " + member))
                .andExpect(status().isNotFound()).andReturn())).isEqualTo("API_KEY_NOT_FOUND");

        String outsider = adminOf("other-" + domain);
        mvc.perform(delete(KEYS + "/" + memberKey).header("Authorization", "Bearer " + outsider))
                .andExpect(status().isNotFound());

        mvc.perform(delete(KEYS + "/" + memberKey).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        mvc.perform(delete(KEYS + "/" + memberKey).header("Authorization", "Bearer " + member))
                .andExpect(status().isNoContent());

        JsonNode revoked = body(mvc.perform(get(KEYS).header("Authorization", "Bearer " + member)).andReturn()).get(0);
        assertThat(revoked.get("status").asText()).isEqualTo("REVOKED");
        assertThat(revoked.get("revokedReason").asText()).isEqualTo("REVOKED");
        assertThat(revoked.get("revokedByName").asText()).isEqualTo("Alok Kumar");
        assertThat(auditCount("API_KEY_REVOKED", memberKey)).isEqualTo(1);
    }

    @Test
    @DisplayName("removing a member revokes their personal keys and leaves the workspace's keys alone")
    void removalRevokesPersonalKeys() throws Exception {
        String admin = adminOfNewWorkspace();
        String member = memberOf(admin, "sara");
        String memberKey = body(create(member, personalKey())).at("/key/id").asText();
        String serviceKey = body(create(admin, serviceKey())).at("/key/id").asText();

        mvc.perform(delete("/api/v1/members/" + memberIdOf(admin, "sara@" + domain))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        assertThat(db.queryForObject("select revoked_reason from app_lm_api_key where id = ?::uuid",
                String.class, memberKey)).isEqualTo("MEMBER_REMOVED");
        assertThat(db.queryForObject("select revoked_at is null from app_lm_api_key where id = ?::uuid",
                Boolean.class, serviceKey)).isTrue();
    }

    @Test
    @DisplayName("an expiry past the ceiling, an unknown scope or kind, or no scope is refused, and so is an eleventh live key")
    void limits() throws Exception {
        String admin = adminOfNewWorkspace();

        MvcResult tooLong = create(admin, """
                {"name":"Too long","scopes":["projects:read"],"expiresInDays":366}""");
        assertThat(tooLong.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(tooLong).get("detail").asText()).contains("365 days");
        assertThat(create(admin, """
                {"name":"Writes","scopes":["projects:write"]}""").getResponse().getStatus()).isEqualTo(400);
        assertThat(create(admin, """
                {"name":"Nothing","scopes":[]}""").getResponse().getStatus()).isEqualTo(400);
        assertThat(create(admin, """
                {"name":"Null","scopes":[null]}""").getResponse().getStatus()).isEqualTo(400);
        assertThat(create(admin, """
                {"name":"Odd kind","kind":"ROBOT","scopes":["projects:read"]}""").getResponse().getStatus())
                .isEqualTo(400);

        for (int i = 0; i < 10; i++) {
            assertThat(create(admin, personalKey()).getResponse().getStatus()).isEqualTo(201);
        }
        assertThat(codeOf(create(admin, personalKey()))).isEqualTo("API_KEY_LIMIT_REACHED");
        assertThat(create(admin, serviceKey()).getResponse().getStatus()).isEqualTo(201);
    }

    private String adminOfNewWorkspace() throws Exception {
        return adminOf(domain);
    }

    private String adminOf(String emailDomain) throws Exception {
        String alok = "alok@" + emailDomain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Keys Firm");
        return login(alok);
    }

    private String memberOf(String admin, String localPart) throws Exception {
        String address = localPart + "@" + domain;
        inviteAndAccept(admin, "Sara Al-Mansour", address, "MEMBER");
        return login(address);
    }

    private MvcResult create(String bearer, String requestBody) throws Exception {
        return mvc.perform(post(KEYS).header("Authorization", "Bearer " + bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andReturn();
    }

    private static String personalKey() {
        return """
                {"name":"Script","scopes":["projects:read"]}""";
    }

    private static String serviceKey() {
        return """
                {"name":"ATS sync","kind":"SERVICE","scopes":["projects:read","candidates:read"]}""";
    }

    private int auditCount(String eventType, String keyId) {
        return db.queryForObject("""
                select count(*) from app_lm_audit_event
                where event_type = ? and target_type = 'api_key' and target_id = ?""", Integer.class, eventType, keyId);
    }
}
