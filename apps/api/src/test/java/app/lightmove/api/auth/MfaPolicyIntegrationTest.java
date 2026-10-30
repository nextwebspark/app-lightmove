package app.lightmove.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.core.security.mfa.TestTotp;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * A workspace that requires two-factor authentication (#1082): a member without a factor reads nothing of it but
 * the way to set one up, wherever the session came from, and an admin may reset a lost one.
 */
@IntegrationTest
class MfaPolicyIntegrationTest extends FlowTestSupport {

    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("a member without a factor is refused every workspace read, but not enrolment, until they set one up")
    void theGateHoldsUntilEnrolment() throws Exception {
        String sara = "sara@" + domain;
        String workspaceId = createWorkspace(verifiedUser("Sara G", sara), "Strict Firm");
        requireMfa(workspaceId);
        MvcResult session = loginRaw(sara);
        String token = bearerOf(session);

        assertThat(codeOf(getAs(token, "/api/v1/projects"))).isEqualTo("MFA_ENROLMENT_REQUIRED");
        assertThat(getAs(token, "/api/v1/projects").getResponse().getStatus()).isEqualTo(403);
        assertThat(me(token).get("twoFactorEnrolmentRequired").asBoolean()).isTrue();
        assertThat(getAs(token, "/api/v1/auth/mfa").getResponse().getStatus()).isEqualTo(200);

        enrolFrom(token, session.getResponse().getCookie("lm_refresh"));

        assertThat(getAs(token, "/api/v1/projects").getResponse().getStatus()).isEqualTo(200);
        assertThat(me(token).get("twoFactorEnrolmentRequired").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("a pure client representative is held to the requirement as staff are")
    void pureClientsAreIncluded() throws Exception {
        String sara = "sara@" + domain;
        String workspaceId = createWorkspace(verifiedUser("Sara G", sara), "Strict Firm");
        String representative = clientRepresentative(login(sara), "Rana Client", "rana@" + domain);
        requireMfa(workspaceId);

        assertThat(codeOf(getAs(representative, "/api/v1/projects"))).isEqualTo("MFA_ENROLMENT_REQUIRED");
        assertThat(me(representative).get("twoFactorEnrolmentRequired").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("a workspace that requires nothing reads as before")
    void aWorkspaceWithoutTheRequirementIsUntouched() throws Exception {
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Sara G", sara), "Relaxed Firm");
        String token = login(sara);

        assertThat(getAs(token, "/api/v1/projects").getResponse().getStatus()).isEqualTo(200);
        assertThat(me(token).get("twoFactorEnrolmentRequired").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("only an admin who holds a factor turns the requirement on, and the change is audited")
    void turningTheRequirementOn() throws Exception {
        String sara = "sara@" + domain;
        String omar = "omar@" + domain;
        String workspaceId = createWorkspace(verifiedUser("Sara G", sara), "Strict Firm");
        String admin = login(sara);
        inviteAndAccept(admin, "Omar K", omar, "MEMBER");
        String member = login(omar);

        assertThat(codeOf(putRequirement(member, true))).isEqualTo("FORBIDDEN");
        assertThat(codeOf(putRequirement(admin, true))).isEqualTo("MFA_ENABLE_YOURS_FIRST");

        MvcResult session = loginRaw(sara);
        enrolFrom(bearerOf(session), session.getResponse().getCookie("lm_refresh"));
        MvcResult turnedOn = putRequirement(bearerOf(session), true);

        assertThat(turnedOn.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(turnedOn).get("requireMfa").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM app_lm_audit_event
                WHERE event_type = 'WORKSPACE_UPDATED' AND workspace_id = ?::uuid
                  AND metadata ->> 'section' = 'requireMfa' AND metadata ->> 'to' = 'true'
                """, Integer.class, workspaceId)).isEqualTo(1);
        assertThat(codeOf(getAs(member, "/api/v1/projects"))).isEqualTo("MFA_ENROLMENT_REQUIRED");
        assertThat(getAs(bearerOf(session), "/api/v1/projects").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("a factor a workspace requires cannot be turned off, and the card says which workspace")
    void turningOffIsRefusedWhileRequired() throws Exception {
        String sara = "sara@" + domain;
        String workspaceId = createWorkspace(verifiedUser("Sara G", sara), "Strict Firm");
        MvcResult session = loginRaw(sara);
        Cookie refresh = session.getResponse().getCookie("lm_refresh");
        String secret = enrolFrom(bearerOf(session), refresh);
        requireMfa(workspaceId);

        JsonNode status = body(getAs(bearerOf(session), "/api/v1/auth/mfa"));
        assertThat(status.get("requiredBy").get(0).asText()).isEqualTo("Strict Firm");

        MvcResult refused = postAs(bearerOf(session), refresh, "/api/v1/auth/mfa/disable",
                Map.of("currentPassword", PASSWORD, "code", freshCode(sara, secret)));
        assertThat(codeOf(refused)).isEqualTo("MFA_REQUIRED_BY_WORKSPACE");
    }

    @Test
    @DisplayName("signing in to, or switching into, a workspace that requires a factor lands on enrolment")
    void switchingAppliesTheTargetsRequirement() throws Exception {
        String sara = "sara@" + domain;
        String alok = "alok@" + domain;
        String strict = createWorkspace(verifiedUser("Sara G", sara), "Strict Firm");
        String own = createWorkspace(verifiedUser("Alok K", alok), "Alok Firm");
        inviteExistingUser(login(sara), alok);
        requireMfa(strict);

        // Accepting was the last choice made, so a fresh sign-in opens in the strict workspace.
        MvcResult session = loginRaw(alok);
        assertThat(body(session).at("/user/twoFactorEnrolmentRequired").asBoolean()).isTrue();
        assertThat(codeOf(getAs(bearerOf(session), "/api/v1/projects"))).isEqualTo("MFA_ENROLMENT_REQUIRED");

        MvcResult intoOwn = switchTo(bearerOf(session), session.getResponse().getCookie("lm_refresh"), own);
        assertThat(body(intoOwn).at("/user/twoFactorEnrolmentRequired").asBoolean()).isFalse();
        assertThat(getAs(bearerOf(intoOwn), "/api/v1/projects").getResponse().getStatus()).isEqualTo(200);

        MvcResult backIntoStrict = switchTo(bearerOf(intoOwn), intoOwn.getResponse().getCookie("lm_refresh"), strict);
        assertThat(body(backIntoStrict).at("/user/twoFactorEnrolmentRequired").asBoolean()).isTrue();
        assertThat(codeOf(getAs(bearerOf(backIntoStrict), "/api/v1/projects"))).isEqualTo("MFA_ENROLMENT_REQUIRED");
    }

    @Test
    @DisplayName("a session held at enrolment cannot pair the extension into its workspace")
    void extensionPairingIsRefusedWhileEnrolmentIsOwed() throws Exception {
        String sara = "sara@" + domain;
        String workspaceId = createWorkspace(verifiedUser("Sara G", sara), "Strict Firm");
        requireMfa(workspaceId);
        String token = login(sara);

        MvcResult refused = mvc.perform(post("/api/v1/auth/extension/tokens")
                        .header("Authorization", "Bearer " + token))
                .andReturn();

        assertThat(codeOf(refused)).isEqualTo("MFA_ENROLMENT_REQUIRED");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM app_lm_refresh_token t JOIN app_lm_user u ON u.id = t.user_id
                WHERE u.email = ? AND t.client = 'BROWSER_EXTENSION'
                """, Integer.class, sara)).isZero();
    }

    @Test
    @DisplayName("an admin resets a member's factor: the member is signed out, emailed, and the reset is audited")
    void anAdminResetsAMembersFactor() throws Exception {
        String sara = "sara@" + domain;
        String omar = "omar@" + domain;
        String alok = "alok@" + domain;
        String strict = createWorkspace(verifiedUser("Sara G", sara), "Strict Firm");
        MvcResult adminSession = loginRaw(sara);
        String admin = bearerOf(adminSession);
        enrolFrom(admin, adminSession.getResponse().getCookie("lm_refresh"));
        inviteAndAccept(admin, "Omar K", omar, "MEMBER");
        createWorkspace(verifiedUser("Alok K", alok), "Alok Firm");
        inviteExistingUser(admin, alok);

        MvcResult omarSession = loginRaw(omar);
        enrolFrom(bearerOf(omarSession), omarSession.getResponse().getCookie("lm_refresh"));
        MvcResult alokSession = loginRaw(alok);
        enrolFrom(bearerOf(alokSession), alokSession.getResponse().getCookie("lm_refresh"));
        requireMfa(strict);

        JsonNode overview = body(getAs(admin, "/api/v1/workspace/two-factor"));
        assertThat(overview.get("required").asBoolean()).isTrue();
        assertThat(rowOf(overview, omar).get("enabled").asBoolean()).isTrue();
        assertThat(rowOf(overview, omar).get("resettable").asBoolean()).isTrue();
        assertThat(rowOf(overview, alok).get("resettable").asBoolean()).isFalse();
        assertThat(rowOf(overview, sara).get("resettable").asBoolean()).isFalse();
        assertThat(codeOf(getAs(bearerOf(omarSession), "/api/v1/workspace/two-factor"))).isEqualTo("FORBIDDEN");

        assertThat(codeOf(resetAs(bearerOf(omarSession), memberIdOf(admin, sara)))).isEqualTo("FORBIDDEN");
        assertThat(codeOf(resetAs(admin, memberIdOf(admin, sara)))).isEqualTo("MFA_RESET_SELF");
        assertThat(codeOf(resetAs(admin, memberIdOf(admin, alok)))).isEqualTo("MFA_RESET_OTHER_WORKSPACES");

        email.clear();
        MvcResult reset = resetAs(admin, memberIdOf(admin, omar));

        assertThat(reset.getResponse().getStatus()).isEqualTo(204);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM app_lm_user_mfa_factor f JOIN app_lm_user u ON u.id = f.user_id WHERE u.email = ?
                """, Integer.class, omar)).isZero();
        assertThat(mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(omarSession.getResponse().getCookie("lm_refresh")).with(csrf()))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(email.subjectsFor(omar)).contains("Your Uncava two-factor authentication was reset");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM app_lm_audit_event e JOIN app_lm_user u ON u.id = e.actor_user_id
                WHERE e.event_type = 'MFA_RESET_BY_ADMIN' AND u.email = ? AND e.workspace_id = ?::uuid
                """, Integer.class, sara, strict)).isEqualTo(1);

        // Signed in again with the password alone, and held at enrolment by the requirement.
        String omarAgain = login(omar);
        assertThat(codeOf(getAs(omarAgain, "/api/v1/projects"))).isEqualTo("MFA_ENROLMENT_REQUIRED");
    }

    // Helpers

    private void requireMfa(String workspaceId) {
        jdbc.update("UPDATE app_lm_workspace SET require_mfa = true WHERE id = ?::uuid", workspaceId);
    }

    /** @return the factor's secret */
    private String enrolFrom(String token, Cookie refresh) throws Exception {
        String secret = body(postAs(token, refresh, "/api/v1/auth/mfa/setup", null)).get("secret").asText();
        MvcResult enabled = postAs(token, refresh, "/api/v1/auth/mfa/enable",
                Map.of("currentPassword", PASSWORD, "code", TestTotp.codeAt(secret, Instant.now())));
        assertThat(enabled.getResponse().getStatus()).as(enabled.getResponse().getContentAsString()).isEqualTo(200);
        return secret;
    }

    private String freshCode(String emailAddress, String secret) {
        jdbc.update("UPDATE app_lm_user_mfa_factor SET last_used_step = NULL WHERE user_id = "
                + "(SELECT id FROM app_lm_user WHERE email = ?)", emailAddress);
        return TestTotp.codeAt(secret, Instant.now());
    }

    private void inviteExistingUser(String adminToken, String inviteeEmail) throws Exception {
        mvc.perform(post("/api/v1/invitations")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        [{"email":"%s","role":"MEMBER"}]
                        """.formatted(inviteeEmail)));
        MvcResult accepted = mvc.perform(post("/api/v1/onboarding/invitations/accept")
                        .header("Authorization", "Bearer " + login(inviteeEmail))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}
                                """.formatted(email.latestTokenFor(inviteeEmail))))
                .andReturn();
        assertThat(accepted.getResponse().getStatus()).as(accepted.getResponse().getContentAsString()).isEqualTo(200);
    }

    private static JsonNode rowOf(JsonNode overview, String emailAddress) {
        for (JsonNode row : overview.get("members")) {
            if (row.get("email").asText().equals(emailAddress)) {
                return row;
            }
        }
        throw new AssertionError(emailAddress + " not listed: " + overview);
    }

    private MvcResult putRequirement(String token, boolean required) throws Exception {
        return mvc.perform(put("/api/v1/workspace/require-mfa")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"required\":" + required + "}"))
                .andReturn();
    }

    private MvcResult resetAs(String token, String memberId) throws Exception {
        return mvc.perform(delete("/api/v1/members/" + memberId + "/two-factor")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
    }

    private MvcResult switchTo(String token, Cookie refresh, String workspaceId) throws Exception {
        MvcResult switched = mvc.perform(post("/api/v1/auth/switch-workspace")
                        .header("Authorization", "Bearer " + token)
                        .cookie(refresh)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workspaceId\":\"" + workspaceId + "\"}"))
                .andReturn();
        assertThat(switched.getResponse().getStatus()).as(switched.getResponse().getContentAsString()).isEqualTo(200);
        return switched;
    }

    private JsonNode me(String token) throws Exception {
        return body(getAs(token, "/api/v1/auth/me"));
    }

    private MvcResult getAs(String token, String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", "Bearer " + token)).andReturn();
    }

    private MvcResult postAs(String token, Cookie refresh, String path, Map<String, String> payload)
            throws Exception {
        var request = post(path).header("Authorization", "Bearer " + token).with(csrf());
        if (refresh != null) {
            request.cookie(refresh);
        }
        if (payload != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload));
        }
        return mvc.perform(request).andReturn();
    }

    private MvcResult loginRaw(String emailAddress) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(emailAddress, PASSWORD)))
                .andReturn();
        assertThat(body(result).has("accessToken")).as(result.getResponse().getContentAsString()).isTrue();
        return result;
    }

    private String bearerOf(MvcResult session) throws Exception {
        return body(session).get("accessToken").asText();
    }
}
