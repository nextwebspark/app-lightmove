package app.lightmove.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * A session is in exactly one workspace, and {@code /auth/switch-workspace} is the only way it
 * changes. What these pin: the switch is refused unless the caller is an active member of the
 * target, a refusal burns nothing, a bearer for one user cannot rotate another user's cookie, and
 * the data of the workspace just left is not reachable from the one entered.
 */
@IntegrationTest
class WorkspaceSwitchIntegrationTest extends FlowTestSupport {

    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("a member of two workspaces switches between them, and each session sees one tenant's data")
    void switchMovesTheSessionAndIsolatesData() throws Exception {
        String alokEmail = "alok@" + domain;
        String firstId = createWorkspace(verifiedUser("Alok Kumar", alokEmail), "First Firm");
        String first = login(alokEmail);
        String firstProject = createProject(first, createClient(first, "Acme"), "CFO Search");

        String secondId = createSecondWorkspace(first, "Second Firm");
        assertThat(secondId).isNotEqualTo(firstId);

        // The creating session still names the first workspace: nothing moved a token it holds.
        assertThat(wsIdOf(first)).isEqualTo(firstId);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + first))
                .andExpect(jsonPath("$.workspace.id").value(firstId))
                .andExpect(jsonPath("$.workspaces.length()").value(2))
                .andExpect(jsonPath("$.workspaces[1].id").value(secondId))
                .andExpect(jsonPath("$.workspaces[1].roles[0]").value("ADMIN"));

        // Creating was an explicit choice, so a fresh sign-in opens in the second workspace — and from
        // there the first firm's mandate does not exist.
        MvcResult session = loginRaw(alokEmail);
        String second = bearerOf(session);
        assertThat(wsIdOf(second)).isEqualTo(secondId);
        mvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/projects/" + firstProject + "/activity").header("Authorization", "Bearer " + second))
                .andExpect(status().isNotFound());

        MvcResult switched = mvc.perform(post("/api/v1/auth/switch-workspace")
                        .header("Authorization", "Bearer " + second)
                        .cookie(refreshCookie(session))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(switchBody(firstId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.workspace.id").value(firstId))
                .andReturn();
        String backInFirst = bearerOf(switched);
        assertThat(wsIdOf(backInFirst)).isEqualTo(firstId);

        // The cookie rotated as a refresh does: a successor, not the token presented. (Replaying the
        // spent one here would be read as theft and kill the family — AuthFlowIntegrationTest pins that.)
        Cookie successor = refreshCookie(switched);
        assertThat(successor.getValue()).isNotEqualTo(refreshCookie(session).getValue());

        // Tenant isolation across the switch: the mandate is reachable again, and only from here.
        mvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + backInFirst))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(firstProject));
        mvc.perform(get("/api/v1/projects/" + firstProject + "/activity").header("Authorization", "Bearer " + backInFirst))
                .andExpect(status().isOk());

        // A refresh keeps the session where the switch put it — the family remembers.
        MvcResult refreshed = mvc.perform(post("/api/v1/auth/refresh").cookie(successor).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(wsIdOf(bearerOf(refreshed))).isEqualTo(firstId);

        // And the next sign-in opens in the last workspace chosen.
        assertThat(wsIdOf(login(alokEmail))).isEqualTo(firstId);

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM app_lm_audit_event event
                JOIN app_lm_user actor ON actor.id = event.actor_user_id
                WHERE event.event_type = 'WORKSPACE_SWITCHED' AND actor.email = ? AND event.workspace_id = ?::uuid
                """, Integer.class, alokEmail, firstId)).isEqualTo(1);
    }

    @Test
    @DisplayName("switching to a workspace you are not in is a 404 that burns nothing")
    void switchToAForeignWorkspaceIsRefusedWithoutSpendingTheCookie() throws Exception {
        String alokEmail = "alok@" + domain;
        String saraEmail = "sara@" + domain;
        String alokWorkspace = createWorkspace(verifiedUser("Alok Kumar", alokEmail), "Alok Firm");
        String saraWorkspace = createWorkspace(verifiedUser("Sara Al-Mansour", saraEmail), "Sara Firm");

        MvcResult session = loginRaw(alokEmail);
        Cookie cookie = refreshCookie(session);

        for (String target : new String[] {saraWorkspace, UUID.randomUUID().toString()}) {
            MvcResult refused = mvc.perform(post("/api/v1/auth/switch-workspace")
                            .header("Authorization", "Bearer " + bearerOf(session))
                            .cookie(cookie)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(switchBody(target)))
                    .andExpect(status().isNotFound())
                    .andReturn();
            // A real workspace and a made-up id answer identically: existence is not confirmed.
            assertThat(codeOf(refused)).isEqualTo("NOT_A_MEMBER");
            assertThat(refused.getResponse().getCookie("lm_refresh"))
                    .as("a refused switch must not touch the cookie").isNull();
        }

        // The presented cookie is still the live one: nothing was rotated, so nothing reads as theft.
        MvcResult refreshed = mvc.perform(post("/api/v1/auth/refresh").cookie(cookie).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(wsIdOf(bearerOf(refreshed))).isEqualTo(alokWorkspace);
    }

    @Test
    @DisplayName("a bearer token cannot move somebody else's cookie")
    void switchRefusesACookieThatIsNotTheBearers() throws Exception {
        String alokEmail = "alok@" + domain;
        String saraEmail = "sara@" + domain;
        String alokWorkspace = createWorkspace(verifiedUser("Alok Kumar", alokEmail), "Alok Firm");
        createWorkspace(verifiedUser("Sara Al-Mansour", saraEmail), "Sara Firm");

        MvcResult sarasSession = loginRaw(saraEmail);
        Cookie sarasCookie = refreshCookie(sarasSession);

        // Alok is a member of the target; the cookie is Sara's. Neither user's session may move.
        mvc.perform(post("/api/v1/auth/switch-workspace")
                        .header("Authorization", "Bearer " + login(alokEmail))
                        .cookie(sarasCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(switchBody(alokWorkspace)))
                .andExpect(status().isUnauthorized());

        // Sara's cookie was not spent by the attempt.
        mvc.perform(post("/api/v1/auth/refresh").cookie(sarasCookie).with(csrf()))
                .andExpect(status().isOk());
    }

    /**
     * A cross-site page can make the browser attach the cookie; it cannot supply the bearer, which
     * lives in this app's memory. So the bearer is the CSRF defence here, and its absence is a 401
     * whatever else the request carries — the cookie alone moves nothing.
     */
    @Test
    @DisplayName("the switch needs a bearer and a cookie; either alone is refused")
    void switchIsGuardedLikeARefresh() throws Exception {
        String alokEmail = "alok@" + domain;
        String workspaceId = createWorkspace(verifiedUser("Alok Kumar", alokEmail), "Alok Firm");
        MvcResult session = loginRaw(alokEmail);

        mvc.perform(post("/api/v1/auth/switch-workspace")
                        .cookie(refreshCookie(session))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(switchBody(workspaceId)))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/auth/switch-workspace")
                        .header("Authorization", "Bearer " + bearerOf(session))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(switchBody(workspaceId)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("being removed from the workspace a session is in moves it to another at the next refresh")
    void removalFallsThroughToAnotherWorkspace() throws Exception {
        String alokEmail = "alok@" + domain;
        String saraEmail = "sara@" + domain;
        String alokWorkspace = createWorkspace(verifiedUser("Alok Kumar", alokEmail), "Alok Firm");
        String saraWorkspace = createWorkspace(verifiedUser("Sara Al-Mansour", saraEmail), "Sara Firm");
        String saraAdmin = login(saraEmail);

        // Alok is invited into Sara's firm and joins; joining is a choice, so his next sign-in is there.
        inviteExistingUser(saraAdmin, alokEmail);
        MvcResult session = loginRaw(alokEmail);
        assertThat(wsIdOf(bearerOf(session))).isEqualTo(saraWorkspace);

        // Sara removes him. His next refresh lands back in his own firm; a switch back is refused.
        mvc.perform(delete("/api/v1/members/" + memberIdOf(saraAdmin, alokEmail))
                        .header("Authorization", "Bearer " + saraAdmin))
                .andExpect(status().isNoContent());

        MvcResult refreshed = mvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie(session)).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(wsIdOf(bearerOf(refreshed))).isEqualTo(alokWorkspace);

        mvc.perform(post("/api/v1/auth/switch-workspace")
                        .header("Authorization", "Bearer " + bearerOf(refreshed))
                        .cookie(refreshCookie(refreshed))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(switchBody(saraWorkspace)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("the extension is paired into the web session's workspace and stays there when the web app switches")
    void extensionKeepsTheWorkspaceItWasPairedIn() throws Exception {
        String alokEmail = "alok@" + domain;
        String firstId = createWorkspace(verifiedUser("Alok Kumar", alokEmail), "First Firm");
        String first = login(alokEmail);
        String secondId = createSecondWorkspace(first, "Second Firm");

        // A fresh sign-in opens in the second workspace, the one just created.
        MvcResult session = loginRaw(alokEmail);
        assertThat(wsIdOf(bearerOf(session))).isEqualTo(secondId);

        // Paired while the web session is in the second workspace.
        JsonNode paired = body(mvc.perform(post("/api/v1/auth/extension/tokens")
                        .header("Authorization", "Bearer " + bearerOf(session)))
                .andExpect(status().isCreated())
                .andReturn());
        assertThat(wsIdOf(paired.get("accessToken").asText())).isEqualTo(secondId);

        // The web app switches back to the first. The extension's own family is untouched.
        mvc.perform(post("/api/v1/auth/switch-workspace")
                        .header("Authorization", "Bearer " + bearerOf(session))
                        .cookie(refreshCookie(session))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(switchBody(firstId)))
                .andExpect(status().isOk());

        JsonNode extensionRefreshed = body(mvc.perform(post("/api/v1/auth/extension/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(paired.get("refreshToken").asText())))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(wsIdOf(extensionRefreshed.get("accessToken").asText())).isEqualTo(secondId);
    }

    // helpers

    private String createSecondWorkspace(String bearerToken, String name) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","companySize":"11-50 people","primaryRegion":"GCC",
                                 "teamFocus":"Executive search"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).at("/workspace/id").asText();
    }

    private String createClient(String bearerToken, String name) throws Exception {
        return body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"%s"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private String createProject(String bearerToken, String clientId, String positionTitle) throws Exception {
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"%s"}
                                """.formatted(clientId, positionTitle)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    /** Invites an address that already has an account, who then accepts the emailed link signed in as themselves. */
    private void inviteExistingUser(String adminToken, String inviteeEmail) throws Exception {
        mvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"email":"%s","role":"MEMBER"}]
                                """.formatted(inviteeEmail)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/onboarding/invitations/accept")
                        .header("Authorization", "Bearer " + login(inviteeEmail))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}
                                """.formatted(email.latestTokenFor(inviteeEmail))))
                .andExpect(status().isOk());
    }

    private MvcResult loginRaw(String emailAddress) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(emailAddress, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String bearerOf(MvcResult session) throws Exception {
        return body(session).get("accessToken").asText();
    }

    private Cookie refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("lm_refresh");
        assertThat(cookie).as("refresh cookie should be set").isNotNull();
        return cookie;
    }

    private static String switchBody(String workspaceId) {
        return """
                {"workspaceId":"%s"}
                """.formatted(workspaceId);
    }

    /** The {@code wsId} claim, read straight off the token: the signed truth about where a session is. */
    private String wsIdOf(String accessToken) throws Exception {
        String payload = accessToken.split("\\.")[1];
        JsonNode claims = json.readTree(Base64.getUrlDecoder().decode(payload));
        JsonNode wsId = claims.get("wsId");
        return wsId == null ? null : wsId.asText();
    }
}
