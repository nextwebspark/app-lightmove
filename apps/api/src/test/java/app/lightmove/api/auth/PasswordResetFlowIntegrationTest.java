package app.lightmove.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * The forgot-password journey, end to end: request a link, redeem it, and walk straight into the app
 * with the session it returns — plus every way the token must refuse to be spent twice, spent late,
 * or spent as something it is not.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class PasswordResetFlowIntegrationTest extends FlowTestSupport {

    private static final String NEW_PASSWORD = "brandnew42";

    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;

    // ── The happy path ────────────────────────────────────────────────────────

    @Test
    @DisplayName("reset changes the password and signs the user straight in")
    void resetChangesPasswordAndAutoLogsIn() throws Exception {
        String alok = "alok@" + domain;
        String token = verifiedUser("Alok Kumar", alok);
        createWorkspace(token, "Reset & Co");

        forgot(alok);
        MvcResult reset = resetRaw(email.latestTokenFor(alok), NEW_PASSWORD);

        // The response is a full login: access token, user with their workspace, refresh cookie.
        assertThat(reset.getResponse().getStatus()).isEqualTo(200);
        JsonNode session = body(reset);
        assertThat(session.get("accessToken").asText()).isNotBlank();
        assertThat(session.at("/user/emailVerified").asBoolean()).isTrue();
        assertThat(session.at("/user/workspace/name").asText()).isEqualTo("Reset & Co");
        assertThat(reset.getResponse().getCookie("lm_refresh")).isNotNull();

        // The old password is dead, the new one works.
        assertThat(codeOf(loginRaw(alok, PASSWORD))).isEqualTo("INVALID_CREDENTIALS");
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(alok, NEW_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("the session issued by the reset survives the revocation of every older one")
    void freshSessionSurvivesRevocation() throws Exception {
        String alok = "alok@" + domain;
        verifiedUser("Alok Kumar", alok);

        // A session from before the reset...
        Cookie preReset = refreshCookie(loginRaw(alok, PASSWORD));

        forgot(alok);
        MvcResult reset = resetRaw(email.latestTokenFor(alok), NEW_PASSWORD);
        Cookie fresh = refreshCookie(reset);

        // ...is revoked by it: whoever knew the old password is out.
        mvc.perform(post("/api/v1/auth/refresh").cookie(preReset).with(csrf()))
                .andExpect(status().isUnauthorized());

        // The reset's own cookie refreshes fine — revocation ran before issuance, not after.
        mvc.perform(post("/api/v1/auth/refresh").cookie(fresh).with(csrf()))
                .andExpect(status().isOk());
    }

    // ── Anti-enumeration ──────────────────────────────────────────────────────

    @Test
    @DisplayName("an unknown address gets the same 202 and no email")
    void unknownAddressIsIndistinguishable() throws Exception {
        String alok = "alok@" + domain;
        verifiedUser("Alok Kumar", alok);
        email.clear();

        MvcResult known = forgotRaw(alok);
        MvcResult unknown = forgotRaw("ghost@" + domain);

        assertThat(unknown.getResponse().getStatus())
                .isEqualTo(known.getResponse().getStatus())
                .isEqualTo(202);
        assertThat(unknown.getResponse().getContentAsString()).isEqualTo(known.getResponse().getContentAsString());
        // Exactly one email went out — the known address's.
        assertThat(email.sent()).hasSize(1);
    }

    // ── Token lifecycle ───────────────────────────────────────────────────────

    @Test
    @DisplayName("a reset link works exactly once")
    void tokenIsSingleUse() throws Exception {
        String alok = "alok@" + domain;
        verifiedUser("Alok Kumar", alok);
        forgot(alok);
        String token = email.latestTokenFor(alok);

        resetOk(token, NEW_PASSWORD);

        assertThat(codeOf(resetRaw(token, "anothernew7"))).isEqualTo("TOKEN_INVALID");
    }

    @Test
    @DisplayName("requesting a new link kills the old one")
    void newRequestSupersedesOldLink() throws Exception {
        String alok = "alok@" + domain;
        verifiedUser("Alok Kumar", alok);

        forgot(alok);
        String first = email.latestTokenFor(alok);
        forgot(alok);
        String second = email.latestTokenFor(alok);

        assertThat(codeOf(resetRaw(first, NEW_PASSWORD))).isEqualTo("TOKEN_INVALID");
        resetOk(second, NEW_PASSWORD);
    }

    @Test
    @DisplayName("an expired link is refused with its own code, so the user knows to ask again")
    void expiredTokenIsRefused() throws Exception {
        String alok = "alok@" + domain;
        verifiedUser("Alok Kumar", alok);
        forgot(alok);
        String token = email.latestTokenFor(alok);

        jdbc.update("""
                UPDATE app_lm_verification_token SET expires_at = ?
                WHERE user_id = (SELECT id FROM app_lm_user WHERE email = ?) AND purpose = 'PASSWORD_RESET'
                """, java.sql.Timestamp.from(Instant.now().minusSeconds(60)), alok);

        assertThat(codeOf(resetRaw(token, NEW_PASSWORD))).isEqualTo("TOKEN_EXPIRED");
    }

    @Test
    @DisplayName("a verification token cannot reset a password, nor a reset token verify an email")
    void purposesDoNotCross() throws Exception {
        String alok = "alok@" + domain;
        signup("Alok Kumar", alok);
        String verificationToken = email.latestTokenFor(alok);

        // The 24-hour verification token must not act as a password-changing credential.
        assertThat(codeOf(resetRaw(verificationToken, NEW_PASSWORD))).isEqualTo("TOKEN_INVALID");

        forgot(alok);
        String resetToken = email.latestTokenFor(alok);

        // And the reset token must not act as verification.
        MvcResult verify = mvc.perform(post("/api/v1/auth/verify").param("token", resetToken)).andReturn();
        assertThat(codeOf(verify)).isEqualTo("TOKEN_INVALID");
    }

    @Test
    @DisplayName("a weak password is refused without burning the link")
    void weakPasswordDoesNotBurnTheLink() throws Exception {
        String alok = "alok@" + domain;
        verifiedUser("Alok Kumar", alok);
        forgot(alok);
        String token = email.latestTokenFor(alok);

        MvcResult weak = resetRaw(token, "short");
        assertThat(weak.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(weak)).isEqualTo("VALIDATION_FAILED");

        // The same link still works — the user retries in place, no second email round-trip.
        resetOk(token, NEW_PASSWORD);
    }

    // ── What redeeming proves ─────────────────────────────────────────────────

    @Test
    @DisplayName("an unverified signup who resets is verified by it — the link proved the mailbox")
    void resetVerifiesAnUnverifiedCreator() throws Exception {
        String alok = "alok@" + domain;
        signup("Alok Kumar", alok);

        forgot(alok);
        MvcResult reset = resetRaw(email.latestTokenFor(alok), NEW_PASSWORD);

        assertThat(reset.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(reset).at("/user/emailVerified").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("a locked-out user recovers by resetting — proving the mailbox beats waiting")
    void resetClearsLockout() throws Exception {
        String alok = "alok@" + domain;
        verifiedUser("Alok Kumar", alok);

        for (int attempt = 0; attempt < 5; attempt++) {
            loginRaw(alok, "wrongpassword1");
        }
        assertThat(codeOf(loginRaw(alok, PASSWORD))).isEqualTo("ACCOUNT_LOCKED");

        forgot(alok);
        resetOk(email.latestTokenFor(alok), NEW_PASSWORD);

        // Locked no more: the new password signs in immediately.
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(alok, NEW_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a Google-only account gains a local password through the reset flow")
    void googleOnlyAccountAttachesPassword() throws Exception {
        String sara = "sara@" + domain;
        users.save(User.registerFederated(sara, "Sara G", null, Instant.now(), "2026-07-01"));

        forgot(sara);
        resetOk(email.latestTokenFor(sara), NEW_PASSWORD);

        // Password sign-in now works alongside Google.
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(sara, NEW_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a suspended account gets the silent 202 and no email")
    void suspendedAccountGetsNoEmail() throws Exception {
        String alok = "alok@" + domain;
        verifiedUser("Alok Kumar", alok);
        jdbc.update("UPDATE app_lm_user SET status = 'SUSPENDED' WHERE email = ?", alok);
        email.clear();

        forgotRaw(alok).getResponse();

        assertThat(email.sent()).isEmpty();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void forgot(String emailAddress) throws Exception {
        forgotRaw(emailAddress);
    }

    private MvcResult forgotRaw(String emailAddress) throws Exception {
        return mvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(emailAddress)))
                .andExpect(status().isAccepted())
                .andReturn();
    }

    private MvcResult resetRaw(String token, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"%s"}
                                """.formatted(token, password)))
                .andReturn();
    }

    private void resetOk(String token, String password) throws Exception {
        MvcResult result = resetRaw(token, password);
        assertThat(result.getResponse().getStatus())
                .as("reset should succeed: %s", result.getResponse().getContentAsString())
                .isEqualTo(200);
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
