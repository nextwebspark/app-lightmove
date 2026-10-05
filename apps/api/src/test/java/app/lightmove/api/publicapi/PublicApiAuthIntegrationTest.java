package app.lightmove.api.publicapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/** An API key is the public API's only credential, the public API is the only place it works, and a dead key is refused alike. */
@IntegrationTest
class PublicApiAuthIntegrationTest extends FlowTestSupport {

    private static final String KEYS = "/api/v1/workspace/api-keys";
    private static final String ME = "/api/v1/public/me";

    @Autowired JdbcTemplate db;

    @Test
    @DisplayName("a live key describes itself, scoped to its workspace, and its use is stamped")
    void liveKey() throws Exception {
        String admin = adminOfNewWorkspace();
        JsonNode created = createKey(admin, """
                {"name":"Power BI","scopes":["companies:read","projects:read"]}""");
        String secret = created.get("secret").asText();
        String keyId = created.at("/key/id").asText();

        JsonNode me = body(mvc.perform(get(ME).header("Authorization", "Bearer " + secret))
                .andExpect(status().isOk()).andReturn());

        assertThat(me.get("id").asText()).isEqualTo(keyId);
        assertThat(me.get("name").asText()).isEqualTo("Power BI");
        assertThat(me.get("kind").asText()).isEqualTo("PERSONAL");
        assertThat(me.get("scopes").toString()).isEqualTo("[\"projects:read\",\"companies:read\"]");
        assertThat(me.get("workspaceId").asText()).isEqualTo(db.queryForObject(
                "select workspace_id::text from app_lm_api_key where id = ?::uuid", String.class, keyId));
        assertThat(db.queryForObject("select last_used_at is not null and last_used_ip is not null "
                + "from app_lm_api_key where id = ?::uuid", Boolean.class, keyId)).isTrue();
    }

    @Test
    @DisplayName("a key opens no internal route, and a session's token opens no public one")
    void credentialsDoNotCross() throws Exception {
        String admin = adminOfNewWorkspace();
        String secret = createKey(admin, personalKey()).get("secret").asText();

        mvc.perform(get(KEYS).header("Authorization", "Bearer " + secret)).andExpect(status().isUnauthorized());
        assertRefused(mvc.perform(get(ME).header("Authorization", "Bearer " + admin)).andReturn());
        assertRefused(mvc.perform(get(ME)).andReturn());
    }

    @Test
    @DisplayName("a mistyped, revoked or expired key is refused, and so is one whose owner left or lost staff access")
    void deadKeys() throws Exception {
        String admin = adminOfNewWorkspace();
        String sara = memberOf(admin, "sara", "Sara Al-Mansour");
        String omar = memberOf(admin, "omar", "Omar Haddad");

        String mistyped = createKey(admin, personalKey()).get("secret").asText();
        mistyped = mistyped.substring(0, mistyped.length() - 1) + (mistyped.endsWith("A") ? "B" : "A");

        JsonNode revoked = createKey(admin, personalKey());
        mvc.perform(delete(KEYS + "/" + revoked.at("/key/id").asText()).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        JsonNode expired = createKey(admin, personalKey());
        db.update("update app_lm_api_key set expires_at = now() - interval '1 minute' where id = ?::uuid",
                expired.at("/key/id").asText());

        String departed = createKey(sara, personalKey()).get("secret").asText();
        mvc.perform(delete("/api/v1/members/" + memberIdOf(admin, "sara@" + domain))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        String demoted = createKey(omar, personalKey()).get("secret").asText();
        db.update("""
                update app_lm_workspace_member_role
                set role_id = (select id from app_lm_role where scope = 'WORKSPACE' and name = 'CLIENT')
                where member_id = ?::uuid""", memberIdOf(admin, "omar@" + domain));

        for (String secret : new String[] {mistyped, revoked.get("secret").asText(), expired.get("secret").asText(),
                departed, demoted}) {
            assertRefused(mvc.perform(get(ME).header("Authorization", "Bearer " + secret)).andReturn());
        }
    }

    @Test
    @DisplayName("each key spends its own budget: past it, 429 with Retry-After, while another key is untouched")
    void rateLimit() throws Exception {
        String admin = adminOfNewWorkspace();
        String service = createKey(admin, """
                {"name":"ATS sync","kind":"SERVICE","scopes":["projects:read"]}""").get("secret").asText();
        String other = createKey(admin, personalKey()).get("secret").asText();

        for (int i = 0; i < 5; i++) {
            mvc.perform(get(ME).header("Authorization", "Bearer " + service)).andExpect(status().isOk());
        }
        MvcResult refused = mvc.perform(get(ME).header("Authorization", "Bearer " + service))
                .andExpect(status().isTooManyRequests()).andReturn();

        assertThat(codeOf(refused)).isEqualTo("RATE_LIMITED");
        assertThat(refused.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("12");
        mvc.perform(get(ME).header("Authorization", "Bearer " + other)).andExpect(status().isOk());
    }

    private void assertRefused(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
        assertThat(codeOf(result)).isEqualTo("API_KEY_INVALID");
    }

    private String adminOfNewWorkspace() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Keys Firm");
        return login(alok);
    }

    private String memberOf(String admin, String localPart, String name) throws Exception {
        String address = localPart + "@" + domain;
        inviteAndAccept(admin, name, address, "MEMBER");
        return login(address);
    }

    private JsonNode createKey(String bearer, String requestBody) throws Exception {
        return body(mvc.perform(post(KEYS).header("Authorization", "Bearer " + bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isCreated()).andReturn());
    }

    private static String personalKey() {
        return """
                {"name":"Script","scopes":["projects:read"]}""";
    }
}
