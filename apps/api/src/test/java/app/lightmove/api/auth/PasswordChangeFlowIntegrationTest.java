package app.lightmove.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * Settings → Security's change-password endpoint: what it takes to be allowed through, and what it
 * costs every other device when someone is.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class PasswordChangeFlowIntegrationTest extends FlowTestSupport {

    private static final String NEW_PASSWORD = "brandnew42";

    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("the change swaps the password and hands back a working session")
    void changeSwapsThePasswordAndReturnsASession() throws Exception {
        String alok = "alok@" + domain;
        String token = verifiedUser("Alok Kumar", alok);
        createWorkspace(token, "Change & Co");

        MvcResult changed = changeRaw(login(alok), PASSWORD, NEW_PASSWORD);

        assertThat(changed.getResponse().getStatus()).isEqualTo(200);
        JsonNode session = body(changed);
        assertThat(session.get("accessToken").asText()).isNotBlank();
        assertThat(session.at("/user/workspace/name").asText()).isEqualTo("Change & Co");
        assertThat(changed.getResponse().getCookie("lm_refresh")).isNotNull();

        assertThat(codeOf(loginRaw(alok, PASSWORD))).isEqualTo("INVALID_CREDENTIALS");
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(alok, NEW_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("every other session dies and the one the change returned survives")
    void otherSessionsDieAndTheFreshOneSurvives() throws Exception {
        String alok = "alok@" + domain;
        verifiedUser("Alok Kumar", alok);

        Cookie beforeTheChange = refreshCookie(loginRaw(alok, PASSWORD));
        MvcResult changed = changeRaw(login(alok), PASSWORD, NEW_PASSWORD);

        mvc.perform(post("/api/v1/auth/refresh").cookie(beforeTheChange).with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie(changed)).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("the mailbox is told, because it is the one channel a thief would not control")
    void theOwnerIsEmailed() throws Exception {
        String alok = "alok@" + domain;
        String token = verifiedUser("Alok Kumar", alok);
        email.clear();

        changeRaw(token, PASSWORD, NEW_PASSWORD);

        assertThat(email.sent()).singleElement()
                .satisfies(message -> assertThat(message.subject()).contains("password was changed"));
    }

    @Test
    @DisplayName("a wrong current password is refused on its own field and changes nothing")
    void wrongCurrentPasswordLeavesTheAccountAlone() throws Exception {
        String alok = "alok@" + domain;
        String token = verifiedUser("Alok Kumar", alok);

        MvcResult refused = changeRaw(token, "notmypassword1", NEW_PASSWORD);

        assertThat(refused.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(refused)).isEqualTo("CURRENT_PASSWORD_INVALID");
        assertThat(body(refused).at("/fieldErrors/currentPassword").asText()).isNotBlank();

        // Neither password was adopted: the old one still opens the account.
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(alok, PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("the new password must clear the policy, and must not be the old one")
    void theNewPasswordIsValidated() throws Exception {
        String alok = "alok@" + domain;
        String token = verifiedUser("Alok Kumar", alok);

        assertThat(codeOf(changeRaw(token, PASSWORD, "nodigitshere"))).isEqualTo("VALIDATION_FAILED");

        MvcResult unchanged = changeRaw(token, PASSWORD, PASSWORD);
        assertThat(codeOf(unchanged)).isEqualTo("VALIDATION_FAILED");
        assertThat(body(unchanged).at("/fieldErrors/newPassword").asText()).contains("different");
    }

    @Test
    @DisplayName("a provider-only account has no current password to verify")
    void providerOnlyAccountHasNothingToChange() throws Exception {
        String sara = "sara@" + domain;
        String token = verifiedUser("Sara G", sara);

        // What a Google-only account looks like: a session, and no local hash. Attaching one is the
        // reset flow's job, since only that proves the mailbox first.
        jdbc.update("UPDATE app_lm_user SET password_hash = NULL WHERE email = ?", sara);

        assertThat(codeOf(changeRaw(token, "anything1", NEW_PASSWORD))).isEqualTo("PASSWORD_NOT_SET");
    }

    @Test
    @DisplayName("a suspended account cannot mint itself a fresh session by changing its password")
    void aSuspendedAccountIsRefused() throws Exception {
        String alok = "alok@" + domain;
        String token = verifiedUser("Alok Kumar", alok);

        // The access token stays valid for up to 15 minutes after a suspension, by design. What must not
        // happen is this endpoint issuing a new one and making that window renewable forever.
        jdbc.update("UPDATE app_lm_user SET status = 'SUSPENDED' WHERE email = ?", alok);

        MvcResult refused = changeRaw(token, PASSWORD, NEW_PASSWORD);

        assertThat(refused.getResponse().getStatus()).isEqualTo(403);
        assertThat(codeOf(refused)).isEqualTo("ACCOUNT_SUSPENDED");
        assertThat(refused.getResponse().getCookie("lm_refresh")).isNull();
    }

    @Test
    @DisplayName("a caller with no session at all cannot reach it")
    void theEndpointIsAuthenticated() throws Exception {
        mvc.perform(post("/api/v1/auth/password/change").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"%s","newPassword":"%s"}
                                """.formatted(PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private MvcResult changeRaw(String bearerToken, String currentPassword, String newPassword) throws Exception {
        return mvc.perform(post("/api/v1/auth/password/change")
                        .header("Authorization", "Bearer " + bearerToken)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"%s","newPassword":"%s"}
                                """.formatted(currentPassword, newPassword)))
                .andReturn();
    }

    private MvcResult loginRaw(String emailAddress, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(emailAddress, password)))
                .andReturn();
    }

    private Cookie refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("lm_refresh");
        assertThat(cookie).as("refresh cookie should be set").isNotNull();
        return cookie;
    }
}
