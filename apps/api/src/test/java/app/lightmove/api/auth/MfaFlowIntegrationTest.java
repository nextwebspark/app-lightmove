package app.lightmove.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.core.security.mfa.TestTotp;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * Two-factor authentication end to end: setting it up, the challenge a right password earns instead of a session,
 * recovery codes, and the lock a wrong code counts towards.
 */
@IntegrationTest
class MfaFlowIntegrationTest extends FlowTestSupport {

    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("a right password earns a challenge, not a session, and the code completes the sign-in")
    void passwordThenCodeSignsIn() throws Exception {
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Sara G", sara), "Two Step & Co");
        Enrolment enrolment = enrol(sara);

        MvcResult challenged = loginRaw(sara, PASSWORD);

        assertThat(challenged.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(challenged).get("mfaRequired").asBoolean()).isTrue();
        assertThat(body(challenged).has("accessToken")).isFalse();
        assertThat(challenged.getResponse().getCookie("lm_refresh")).isNull();
        Cookie challenge = challengeCookie(challenged);
        assertThat(challenge.isHttpOnly()).isTrue();
        assertThat(challenge.getPath()).isEqualTo("/api/v1/auth/mfa");

        MvcResult signedIn = verifyRaw(challenge, freshCode(sara, enrolment.secret()));

        assertThat(signedIn.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(signedIn).get("accessToken").asText()).isNotBlank();
        assertThat(body(signedIn).at("/user/workspace/name").asText()).isEqualTo("Two Step & Co");
        assertThat(signedIn.getResponse().getCookie("lm_refresh")).isNotNull();
        assertThat(signedIn.getResponse().getCookie("lm_mfa_challenge").getMaxAge()).isZero();
    }

    @Test
    @DisplayName("a person without a factor signs in exactly as before")
    void noFactorNoChallenge() throws Exception {
        String omar = "omar@" + domain;
        verifiedUser("Omar K", omar);

        MvcResult signedIn = loginRaw(omar, PASSWORD);

        assertThat(body(signedIn).get("accessToken").asText()).isNotBlank();
        assertThat(signedIn.getResponse().getCookie("lm_mfa_challenge")).isNull();
    }

    @Test
    @DisplayName("a recovery code signs in once, and only once")
    void recoveryCodeWorksOnce() throws Exception {
        String sara = "sara@" + domain;
        verifiedUser("Sara G", sara);
        Enrolment enrolment = enrol(sara);
        String recoveryCode = enrolment.recoveryCodes().getFirst().toLowerCase();

        MvcResult first = verifyRaw(challengeCookie(loginRaw(sara, PASSWORD)), recoveryCode);
        assertThat(first.getResponse().getStatus()).isEqualTo(200);

        MvcResult again = verifyRaw(challengeCookie(loginRaw(sara, PASSWORD)), recoveryCode);
        assertThat(codeOf(again)).isEqualTo("MFA_CODE_INVALID");
        assertThat(status(body(first).get("accessToken").asText()).get("recoveryCodesLeft").asLong()).isEqualTo(9);
    }

    @Test
    @DisplayName("a code is refused a second time within its step, even on a fresh challenge")
    void aReplayedCodeIsRefused() throws Exception {
        String sara = "sara@" + domain;
        verifiedUser("Sara G", sara);
        Enrolment enrolment = enrol(sara);
        jdbc.update("UPDATE app_lm_user_mfa_factor SET last_used_step = NULL");

        String code = TestTotp.codeAt(enrolment.secret(), Instant.now());
        assertThat(verifyRaw(challengeCookie(loginRaw(sara, PASSWORD)), code).getResponse().getStatus())
                .isEqualTo(200);

        assertThat(codeOf(verifyRaw(challengeCookie(loginRaw(sara, PASSWORD)), code)))
                .isEqualTo("MFA_CODE_INVALID");
    }

    @Test
    @DisplayName("a challenge signs in once: its cookie replayed with another good code opens nothing")
    void aChallengeIsSingleUse() throws Exception {
        String sara = "sara@" + domain;
        verifiedUser("Sara G", sara);
        Enrolment enrolment = enrol(sara);
        Cookie challenge = challengeCookie(loginRaw(sara, PASSWORD));

        assertThat(verifyRaw(challenge, recoveryCode(enrolment, 0)).getResponse().getStatus()).isEqualTo(200);
        assertThat(codeOf(verifyRaw(challenge, recoveryCode(enrolment, 1)))).isEqualTo("MFA_CODE_INVALID");
    }

    @Test
    @DisplayName("an expired challenge, or none at all, is refused with the same answer as a wrong code")
    void expiredOrMissingChallengeIsRefused() throws Exception {
        String sara = "sara@" + domain;
        verifiedUser("Sara G", sara);
        Enrolment enrolment = enrol(sara);
        Cookie challenge = challengeCookie(loginRaw(sara, PASSWORD));
        jdbc.update("UPDATE app_lm_mfa_challenge SET expires_at = now() - interval '1 minute'");

        assertThat(codeOf(verifyRaw(challenge, recoveryCode(enrolment, 0)))).isEqualTo("MFA_CODE_INVALID");
        assertThat(codeOf(verifyRaw(null, recoveryCode(enrolment, 0)))).isEqualTo("MFA_CODE_INVALID");
    }

    @Test
    @DisplayName("five wrong codes lock the account, with one email, and then even the right code is refused")
    void wrongCodesLockTheAccount() throws Exception {
        String sara = "sara@" + domain;
        verifiedUser("Sara G", sara);
        Enrolment enrolment = enrol(sara);
        Cookie challenge = challengeCookie(loginRaw(sara, PASSWORD));
        email.clear();

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(codeOf(verifyRaw(challenge, wrongCode(enrolment.secret())))).isEqualTo("MFA_CODE_INVALID");
        }

        assertThat(jdbc.queryForObject("SELECT locked_until IS NOT NULL FROM app_lm_user WHERE email = ?",
                Boolean.class, sara)).isTrue();
        assertThat(email.sent()).singleElement()
                .satisfies(message -> assertThat(message.subject()).contains("locked"));
        assertThat(codeOf(verifyRaw(challenge, freshCode(sara, enrolment.secret())))).isEqualTo("MFA_CODE_INVALID");
        assertThat(codeOf(loginRaw(sara, PASSWORD))).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    @DisplayName("a right password does not clear the failed-attempt count; only the right code does")
    void thePasswordAloneDoesNotClearTheCount() throws Exception {
        String sara = "sara@" + domain;
        verifiedUser("Sara G", sara);
        Enrolment enrolment = enrol(sara);
        loginRaw(sara, "wrongpassword1");

        Cookie challenge = challengeCookie(loginRaw(sara, PASSWORD));
        assertThat(failedAttempts(sara)).isEqualTo(1);

        verifyRaw(challenge, freshCode(sara, enrolment.secret()));
        assertThat(failedAttempts(sara)).isZero();
    }

    @Test
    @DisplayName("a password reset sets the password and then asks for the code: the mailbox alone is not enough")
    void aPasswordResetStillAsksForTheCode() throws Exception {
        String sara = "sara@" + domain;
        verifiedUser("Sara G", sara);
        Enrolment enrolment = enrol(sara);

        mvc.perform(post("/api/v1/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s"}
                        """.formatted(sara)));
        MvcResult reset = mvc.perform(post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "token", email.latestTokenFor(sara), "password", "resetpass9"))))
                .andReturn();

        assertThat(body(reset).get("mfaRequired").asBoolean()).isTrue();
        assertThat(reset.getResponse().getCookie("lm_refresh")).isNull();
        assertThat(verifyRaw(challengeCookie(reset), freshCode(sara, enrolment.secret())).getResponse().getStatus())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("turning it on ends every other session and keeps the one it was turned on from")
    void enablingEndsOtherSessions() throws Exception {
        String sara = "sara@" + domain;
        verifiedUser("Sara G", sara);
        Cookie elsewhere = loginRaw(sara, PASSWORD).getResponse().getCookie("lm_refresh");

        MvcResult here = loginRaw(sara, PASSWORD);
        enrolFrom(sara, body(here).get("accessToken").asText(), here.getResponse().getCookie("lm_refresh"));

        assertThat(mvc.perform(post("/api/v1/auth/refresh").cookie(elsewhere).with(csrf()))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mvc.perform(post("/api/v1/auth/refresh").cookie(here.getResponse().getCookie("lm_refresh"))
                .with(csrf())).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(email.sent()).anySatisfy(message ->
                assertThat(message.subject()).contains("Two-factor authentication is on"));
    }

    @Test
    @DisplayName("turning it on asks for the current password and a code from the app, each on its own field")
    void enablingAsksForPasswordAndCode() throws Exception {
        String sara = "sara@" + domain;
        verifiedUser("Sara G", sara);
        MvcResult session = loginRaw(sara, PASSWORD);
        String token = body(session).get("accessToken").asText();
        Cookie refresh = session.getResponse().getCookie("lm_refresh");
        String secret = body(postAs(token, refresh, "/api/v1/auth/mfa/setup", null)).get("secret").asText();

        MvcResult wrongPassword = postAs(token, refresh, "/api/v1/auth/mfa/enable",
                Map.of("currentPassword", "notmine123", "code", TestTotp.codeAt(secret, Instant.now())));
        assertThat(codeOf(wrongPassword)).isEqualTo("CURRENT_PASSWORD_INVALID");
        assertThat(body(wrongPassword).at("/fieldErrors/currentPassword").asText()).isNotBlank();

        MvcResult wrongCode = postAs(token, refresh, "/api/v1/auth/mfa/enable",
                Map.of("currentPassword", PASSWORD, "code", wrongCode(secret)));
        assertThat(codeOf(wrongCode)).isEqualTo("MFA_CODE_INVALID");
        assertThat(body(wrongCode).at("/fieldErrors/code").asText()).isNotBlank();

        assertThat(status(token).get("enabled").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("turning it off asks for the password and a code, and sign-in goes back to the password alone")
    void disablingRestoresPasswordSignIn() throws Exception {
        String sara = "sara@" + domain;
        verifiedUser("Sara G", sara);
        Enrolment enrolment = enrol(sara);
        MvcResult session = verifyRaw(challengeCookie(loginRaw(sara, PASSWORD)), recoveryCode(enrolment, 0));
        String token = body(session).get("accessToken").asText();
        Cookie refresh = session.getResponse().getCookie("lm_refresh");

        assertThat(postAs(token, refresh, "/api/v1/auth/mfa/disable",
                Map.of("currentPassword", PASSWORD, "code", recoveryCode(enrolment, 1))).getResponse().getStatus())
                .isEqualTo(204);

        assertThat(body(loginRaw(sara, PASSWORD)).get("accessToken").asText()).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_lm_user_mfa_recovery_code c "
                + "JOIN app_lm_user u ON u.id = c.user_id WHERE u.email = ?", Long.class, sara)).isZero();
    }

    @Test
    @DisplayName("new recovery codes replace the old set at once")
    void regeneratingReplacesTheSet() throws Exception {
        String sara = "sara@" + domain;
        verifiedUser("Sara G", sara);
        Enrolment enrolment = enrol(sara);
        MvcResult session = verifyRaw(challengeCookie(loginRaw(sara, PASSWORD)), recoveryCode(enrolment, 0));
        String token = body(session).get("accessToken").asText();

        JsonNode fresh = body(postAs(token, session.getResponse().getCookie("lm_refresh"),
                "/api/v1/auth/mfa/recovery-codes",
                Map.of("currentPassword", PASSWORD, "code", recoveryCode(enrolment, 1))));

        assertThat(fresh.get("recoveryCodes")).hasSize(10);
        assertThat(codeOf(verifyRaw(challengeCookie(loginRaw(sara, PASSWORD)), recoveryCode(enrolment, 2))))
                .isEqualTo("MFA_CODE_INVALID");
        assertThat(verifyRaw(challengeCookie(loginRaw(sara, PASSWORD)), fresh.get("recoveryCodes").get(0).asText())
                .getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("after setup, neither the secret nor a recovery code is in any answer or audit line")
    void secretsNeverComeBack() throws Exception {
        String sara = "sara@" + domain;
        verifiedUser("Sara G", sara);
        Enrolment enrolment = enrol(sara);
        String token = body(verifyRaw(challengeCookie(loginRaw(sara, PASSWORD)), recoveryCode(enrolment, 0)))
                .get("accessToken").asText();

        String statusBody = status(token).toString();
        assertThat(statusBody).doesNotContain(enrolment.secret());
        assertThat(statusBody).contains("\"enabled\":true");

        List<String> audited = jdbc.queryForList(
                "SELECT coalesce(metadata::text, '') FROM app_lm_audit_event WHERE event_type LIKE 'MFA_%' "
                        + "OR event_type = 'LOGIN_SUCCEEDED'", String.class);
        assertThat(String.join(" ", audited)).doesNotContain(enrolment.secret());
        for (String code : enrolment.recoveryCodes()) {
            assertThat(String.join(" ", audited)).doesNotContain(code);
        }
        assertThat(jdbc.queryForObject("SELECT secret_encrypted FROM app_lm_user_mfa_factor f "
                + "JOIN app_lm_user u ON u.id = f.user_id WHERE u.email = ?", String.class, sara))
                .doesNotContain(enrolment.secret());
    }

    @Test
    @DisplayName("an account with no password of its own is told to set one before turning it on")
    void providerOnlyAccountIsRefusedSetup() throws Exception {
        String sara = "sara@" + domain;
        String token = verifiedUser("Sara G", sara);
        jdbc.update("UPDATE app_lm_user SET password_hash = NULL WHERE email = ?", sara);

        MvcResult refused = mvc.perform(post("/api/v1/auth/mfa/setup")
                        .header("Authorization", "Bearer " + token).with(csrf()))
                .andReturn();
        assertThat(codeOf(refused)).isEqualTo("PASSWORD_NOT_SET");
    }

    // Helpers

    private record Enrolment(String secret, List<String> recoveryCodes) {
    }

    private Enrolment enrol(String emailAddress) throws Exception {
        MvcResult session = loginRaw(emailAddress, PASSWORD);
        return enrolFrom(emailAddress, body(session).get("accessToken").asText(),
                session.getResponse().getCookie("lm_refresh"));
    }

    private Enrolment enrolFrom(String emailAddress, String token, Cookie refresh) throws Exception {
        String secret = body(postAs(token, refresh, "/api/v1/auth/mfa/setup", null)).get("secret").asText();
        JsonNode enabled = body(postAs(token, refresh, "/api/v1/auth/mfa/enable",
                Map.of("currentPassword", PASSWORD, "code", TestTotp.codeAt(secret, Instant.now()))));
        List<String> codes = new ArrayList<>();
        enabled.get("recoveryCodes").forEach(code -> codes.add(code.asText()));
        assertThat(codes).as("enabling %s answered %s", emailAddress, enabled).hasSize(10);
        return new Enrolment(secret, codes);
    }

    /** A code for now, with the replay guard cleared: each test signs in more often than steps go by. */
    private String freshCode(String emailAddress, String secret) {
        jdbc.update("UPDATE app_lm_user_mfa_factor SET last_used_step = NULL WHERE user_id = "
                + "(SELECT id FROM app_lm_user WHERE email = ?)", emailAddress);
        return TestTotp.codeAt(secret, Instant.now());
    }

    /** Six digits that no step in the window around now produces. */
    private static String wrongCode(String secret) {
        long step = TestTotp.stepAt(Instant.now());
        Set<String> valid = Set.of(TestTotp.codeAtStep(secret, step - 1), TestTotp.codeAtStep(secret, step),
                TestTotp.codeAtStep(secret, step + 1), TestTotp.codeAtStep(secret, step + 2));
        for (int candidate = 0; ; candidate++) {
            String code = "%06d".formatted(candidate);
            if (!valid.contains(code)) {
                return code;
            }
        }
    }

    private static String recoveryCode(Enrolment enrolment, int index) {
        return enrolment.recoveryCodes().get(index);
    }

    private int failedAttempts(String emailAddress) {
        return jdbc.queryForObject("SELECT failed_login_attempts FROM app_lm_user WHERE email = ?",
                Integer.class, emailAddress);
    }

    private JsonNode status(String bearerToken) throws Exception {
        return body(mvc.perform(get("/api/v1/auth/mfa").header("Authorization", "Bearer " + bearerToken))
                .andReturn());
    }

    private MvcResult postAs(String token, Cookie refresh, String path, Map<String, String> payload)
            throws Exception {
        var request = post(path)
                .header("Authorization", "Bearer " + token)
                .with(csrf());
        if (refresh != null) {
            request.cookie(refresh);
        }
        if (payload != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload));
        }
        return mvc.perform(request).andReturn();
    }

    private MvcResult loginRaw(String emailAddress, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(emailAddress, password)))
                .andReturn();
    }

    private MvcResult verifyRaw(Cookie challenge, String code) throws Exception {
        var request = post("/api/v1/auth/mfa/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("code", code)));
        if (challenge != null) {
            request.cookie(challenge);
        }
        return mvc.perform(request).andReturn();
    }

    private Cookie challengeCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("lm_mfa_challenge");
        assertThat(cookie).as("challenge cookie should be set").isNotNull();
        return cookie;
    }
}
