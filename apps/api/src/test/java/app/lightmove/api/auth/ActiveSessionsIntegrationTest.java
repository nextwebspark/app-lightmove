package app.lightmove.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * Settings → Active sessions, end to end.
 *
 * <p>A session is a refresh-token family, and the refresh cookie is what says which family the caller
 * is on — so every assertion here is about a request carrying both a bearer token and a cookie, which
 * is exactly what the browser sends.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class ActiveSessionsIntegrationTest extends FlowTestSupport {

    private static final String MAC_SAFARI = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Safari/605.1.15";
    private static final String IPHONE_SAFARI = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_1 like Mac OS X) "
            + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Mobile/15E148 Safari/604.1";

    @Test
    @DisplayName("each login is one session, and exactly one of them is the caller's own")
    void everyLoginIsOneSessionAndOnlyOneIsCurrent() throws Exception {
        String alok = "alok@" + domain;
        verifiedUser("Alok Kumar", alok);

        SignedInDevice phone = signIn(alok, IPHONE_SAFARI);
        // Measured, not assumed: signup and verification each issue a session of their own, so the
        // baseline is a property of the fixture rather than of the behaviour under test.
        int before = list(phone).size();

        signIn(alok, MAC_SAFARI);
        JsonNode sessions = list(phone);

        assertThat(sessions.size()).isEqualTo(before + 1);
        assertThat(currentCount(sessions)).isEqualTo(1);
        assertThat(deviceOf(sessions, true)).isEqualTo("iPhone — Safari");
        assertThat(labels(sessions)).contains("macOS — Safari");

        // The current session sorts first, so "This device" is the row the user reads before any other.
        assertThat(sessions.get(0).get("current").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("revoking a session kills that one and leaves the caller's alone")
    void revokingEndsOnlyTheNamedSession() throws Exception {
        String alok = "alok@" + domain;
        verifiedUser("Alok Kumar", alok);

        SignedInDevice laptop = signIn(alok, MAC_SAFARI);
        SignedInDevice phone = signIn(alok, IPHONE_SAFARI);
        String laptopSessionId = sessionIdOf(list(laptop), true);

        mvc.perform(delete("/api/v1/auth/sessions/" + laptopSessionId)
                        .header("Authorization", "Bearer " + phone.bearerToken())
                        .cookie(phone.refreshCookie())
                        .with(csrf()))
                .andExpect(status().isNoContent());

        // The revoked device cannot refresh — and is told its session ended, not that it stole a token.
        // USER_REVOKED must stay outside RevokeReason.indicatesTheftOnReplay().
        MvcResult refused = refresh(laptop.refreshCookie());
        assertThat(refused.getResponse().getStatus()).isEqualTo(401);
        assertThat(codeOf(refused)).isEqualTo("REFRESH_TOKEN_INVALID");

        assertThat(refresh(phone.refreshCookie()).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("someone else's session id answers 404, never a 403 that would confirm it is real")
    void anotherUsersSessionIsNotFound() throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        verifiedUser("Alok Kumar", alok);
        verifiedUser("Sara Al-Mansour", sara);

        SignedInDevice hers = signIn(sara, MAC_SAFARI);
        SignedInDevice his = signIn(alok, IPHONE_SAFARI);
        String herSessionId = sessionIdOf(list(hers), true);

        MvcResult refused = mvc.perform(delete("/api/v1/auth/sessions/" + herSessionId)
                        .header("Authorization", "Bearer " + his.bearerToken())
                        .cookie(his.refreshCookie())
                        .with(csrf()))
                .andReturn();

        assertThat(refused.getResponse().getStatus()).isEqualTo(404);
        assertThat(codeOf(refused)).isEqualTo("SESSION_NOT_FOUND");
        assertThat(refresh(hers.refreshCookie()).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("the session you are using is ended by signing out, not by revoking it")
    void theCurrentSessionCannotBeRevoked() throws Exception {
        String alok = "alok@" + domain;
        verifiedUser("Alok Kumar", alok);
        SignedInDevice laptop = signIn(alok, MAC_SAFARI);

        MvcResult refused = mvc.perform(delete("/api/v1/auth/sessions/" + sessionIdOf(list(laptop), true))
                        .header("Authorization", "Bearer " + laptop.bearerToken())
                        .cookie(laptop.refreshCookie())
                        .with(csrf()))
                .andReturn();

        assertThat(refused.getResponse().getStatus()).isEqualTo(409);
        assertThat(codeOf(refused)).isEqualTo("CURRENT_SESSION_NOT_REVOCABLE");
    }

    @Test
    @DisplayName("sign out all others leaves exactly the one you are on, and says how many went")
    void revokeOthersLeavesOnlyTheCaller() throws Exception {
        String alok = "alok@" + domain;
        verifiedUser("Alok Kumar", alok);
        signIn(alok, MAC_SAFARI);
        SignedInDevice phone = signIn(alok, IPHONE_SAFARI);
        int others = list(phone).size() - 1;

        MvcResult result = mvc.perform(post("/api/v1/auth/sessions/revoke-others")
                        .header("Authorization", "Bearer " + phone.bearerToken())
                        .cookie(phone.refreshCookie())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(body(result).get("revoked").asInt()).isEqualTo(others);

        JsonNode remaining = list(phone);
        assertThat(remaining.size()).isEqualTo(1);
        assertThat(remaining.get(0).get("current").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("without the refresh cookie no row could honestly be marked as yours, so the read is refused")
    void theCookieIsRequired() throws Exception {
        String alok = "alok@" + domain;
        verifiedUser("Alok Kumar", alok);
        SignedInDevice laptop = signIn(alok, MAC_SAFARI);

        mvc.perform(get("/api/v1/auth/sessions")
                        .header("Authorization", "Bearer " + laptop.bearerToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the cookie alone is not a credential here — a bearer token is still required")
    void theEndpointIsAuthenticated() throws Exception {
        String alok = "alok@" + domain;
        verifiedUser("Alok Kumar", alok);
        SignedInDevice laptop = signIn(alok, MAC_SAFARI);

        mvc.perform(get("/api/v1/auth/sessions").cookie(laptop.refreshCookie()))
                .andExpect(status().isUnauthorized());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** What a browser holds after signing in: a bearer token in memory and a refresh cookie. */
    private record SignedInDevice(String bearerToken, Cookie refreshCookie) {}

    private SignedInDevice signIn(String emailAddress, String userAgent) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .header("User-Agent", userAgent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(emailAddress, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        return new SignedInDevice(
                body(result).get("accessToken").asText(), result.getResponse().getCookie("lm_refresh"));
    }

    private JsonNode list(SignedInDevice device) throws Exception {
        return body(mvc.perform(get("/api/v1/auth/sessions")
                        .header("Authorization", "Bearer " + device.bearerToken())
                        .cookie(device.refreshCookie()))
                .andExpect(status().isOk())
                .andReturn());
    }

    private MvcResult refresh(Cookie refreshCookie) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie).with(csrf())).andReturn();
    }

    private static long currentCount(JsonNode sessions) {
        return StreamSupport.stream(sessions.spliterator(), false)
                .filter(session -> session.get("current").asBoolean())
                .count();
    }

    private static String sessionIdOf(JsonNode sessions, boolean current) {
        return find(sessions, current).get("id").asText();
    }

    private static String deviceOf(JsonNode sessions, boolean current) {
        return find(sessions, current).get("device").asText();
    }

    private static JsonNode find(JsonNode sessions, boolean current) {
        for (JsonNode session : sessions) {
            if (session.get("current").asBoolean() == current) {
                return session;
            }
        }
        throw new AssertionError("no session with current=" + current + " in " + sessions);
    }

    private static List<String> labels(JsonNode sessions) {
        return StreamSupport.stream(sessions.spliterator(), false)
                .map(session -> session.get("device").asText())
                .toList();
    }
}
