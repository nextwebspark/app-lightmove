package app.lightmove.api.core.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/** Settings → Connected AI apps: who sees and disconnects which grant, and that grants end with their membership. */
@IntegrationTest
class OAuthGrantIntegrationTest extends OAuthFlowSupport {

    private static final String GRANTS = "/api/v1/workspace/oauth-grants";

    @Autowired JdbcTemplate db;
    @Autowired OAuthGrantPurge purge;

    @Test
    @DisplayName("a member lists their own connections; an admin's all=true covers the workspace; a member's is refused")
    void whoSeesWhichGrant() throws Exception {
        String admin = adminOf(domain);
        String member = memberOf(admin, "sara");
        String memberGrant = grantOf(connect(registerClient(), member, "projects:read", "companies:read"));
        String adminGrant = grantOf(connect(registerClient(), admin, "projects:read"));

        JsonNode own = list(member, false);
        assertThat(own).hasSize(1);
        assertThat(own.get(0).get("id").asText()).isEqualTo(memberGrant);
        assertThat(own.get(0).get("clientName").asText()).isEqualTo("Claude");
        assertThat(own.get(0).get("redirectHost").asText()).isEqualTo("claude.ai");
        assertThat(own.get(0).get("scopes").toString()).contains("projects:read", "companies:read");
        assertThat(own.toString()).doesNotContain("token");

        assertThat(list(admin, true).valueStream().map(grant -> grant.get("id").asText()))
                .containsExactlyInAnyOrder(memberGrant, adminGrant);
        mvc.perform(get(GRANTS).param("all", "true").header("Authorization", "Bearer " + member))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a member disconnects only their own; an admin anyone's; another workspace's grant is not found")
    void whoRevokes() throws Exception {
        String admin = adminOf(domain);
        String member = memberOf(admin, "sara");
        String clientId = registerClient();
        JsonNode memberTokens = connect(clientId, member, "projects:read");
        String memberGrant = grantOf(memberTokens);
        String adminGrant = grantOf(connect(registerClient(), admin, "projects:read"));

        assertThat(codeOf(mvc.perform(delete(GRANTS + "/" + adminGrant).header("Authorization", "Bearer " + member))
                .andExpect(status().isNotFound()).andReturn())).isEqualTo("OAUTH_GRANT_NOT_FOUND");
        mvc.perform(delete(GRANTS + "/" + memberGrant).header("Authorization", "Bearer " + adminOf("other-" + domain)))
                .andExpect(status().isNotFound());

        mvc.perform(delete(GRANTS + "/" + memberGrant).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        assertThat(list(member, false)).isEmpty();
        assertThat(body(refresh(clientId, memberTokens.get("refresh_token").asText())).get("error").asText())
                .isEqualTo("invalid_grant");
        assertThat(db.queryForObject("""
                select count(*) from app_lm_audit_event
                where event_type = 'OAUTH_GRANT_REVOKED' and target_id = ? and metadata ->> 'reason' = 'REVOKED'""",
                Integer.class, memberGrant)).isEqualTo(1);
    }

    @Test
    @DisplayName("removing a member ends their grants and leaves everyone else's")
    void removalEndsGrants() throws Exception {
        String admin = adminOf(domain);
        String member = memberOf(admin, "sara");
        String memberGrant = grantOf(connect(registerClient(), member, "projects:read"));
        String adminGrant = grantOf(connect(registerClient(), admin, "projects:read"));

        mvc.perform(delete("/api/v1/members/" + memberIdOf(admin, "sara@" + domain))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        assertThat(exists(memberGrant)).isFalse();
        assertThat(exists(adminGrant)).isTrue();
        assertThat(db.queryForObject("""
                select metadata ->> 'reason' from app_lm_audit_event
                where event_type = 'OAUTH_GRANT_REVOKED' and target_id = ?""", String.class, memberGrant))
                .isEqualTo("MEMBER_REMOVED");
    }

    @Test
    @DisplayName("deleting the workspace ends every grant into it")
    void workspaceDeletionEndsGrants() throws Exception {
        String admin = adminOf(domain);
        String adminGrant = grantOf(connect(registerClient(), admin, "projects:read"));

        mvc.perform(delete("/api/v1/workspace").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirmName\":\"Keys Firm\"}"))
                .andExpect(status().isNoContent());

        assertThat(exists(adminGrant)).isFalse();
    }

    @Test
    @DisplayName("the purge clears a grant once every token has lapsed, and keeps a live one")
    void purgeClearsLapsedGrants() throws Exception {
        String admin = adminOf(domain);
        String grant = grantOf(connect(registerClient(), admin, "projects:read"));

        purge.purgeAt(Instant.now());
        assertThat(exists(grant)).isTrue();

        purge.purgeAt(Instant.now().plus(java.time.Duration.ofDays(31)));
        assertThat(exists(grant)).isFalse();
    }

    private JsonNode list(String bearer, boolean all) throws Exception {
        return body(mvc.perform(get(GRANTS).param("all", Boolean.toString(all))
                .header("Authorization", "Bearer " + bearer)).andExpect(status().isOk()).andReturn());
    }

    private String grantOf(JsonNode tokens) {
        return mcpTokens.decode(tokens.get("access_token").asText()).getClaimAsString("grant_id");
    }

    private boolean exists(String grantId) {
        return db.queryForObject("select count(*) from app_lm_oauth_authorization where id = ?::uuid",
                Integer.class, grantId) == 1;
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
}
