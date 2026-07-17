package app.lightmove.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The developer shortcut: {@code lightmove.auth.auto-verify-email} stamps a signup verified and sends
 * no email.
 *
 * <p>The flag lives here and not in {@code application-test.yml} on purpose. Verification is a security
 * control, and {@link AuthFlowIntegrationTest} is what holds it up — switch this on for the whole suite
 * and those tests would go green while asserting nothing. So this class opts in alone, and the default
 * stays off everywhere else, including CI.
 *
 * <p>It skips exactly one step: proving the mailbox. Invitations are not verification and are not
 * touched by it — {@code AuthFlowIntegrationTest} still owns those.
 */
@IntegrationTest
@TestPropertySource(properties = "lightmove.auth.auto-verify-email=true")
@Import(RecordingEmailSender.Config.class)
class AutoVerifyEmailTest {

    private static final String PASSWORD = "secret123";

    /** Own domain per test, for the same reason as {@link AuthFlowIntegrationTest}: nothing rolls back. */
    private static final AtomicInteger RUN = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RecordingEmailSender email;

    private String alokEmail;

    @BeforeEach
    void reset() {
        email.clear();
        alokEmail = "alok@autoverify%d.example".formatted(RUN.incrementAndGet());
    }

    @Test
    @DisplayName("signup is verified on the spot, and no email is sent")
    void signupIsVerifiedWithoutAnEmail() throws Exception {
        JsonNode session = signup("Alok Kumar", alokEmail);

        assertThat(session.at("/user/emailVerified").asBoolean()).isTrue();
        assertThat(email.sent()).isEmpty();
    }

    /**
     * The token from signup itself must already carry the verified claim.
     *
     * <p>This is the point of verifying <i>before</i> the token is minted, and {@code /members} is the
     * same route {@link AuthFlowIntegrationTest#signupIsUnverifiedAndWorkspaceless()} asserts a 403 on.
     * The two failures are worlds apart and the status says which is which: <b>403</b> is Spring Security
     * refusing the request at the verified-email gate, before any controller runs. <b>404
     * {@code NOT_A_MEMBER}</b> is the request sailing through that gate and reaching the guard bean, which
     * then finds no workspace on the principal — as it should, since a brand-new signup has not joined one
     * yet. Getting the 404 is therefore exactly the win: the gate is open, without a second login.
     */
    @Test
    @DisplayName("the token minted at signup is already past the verified-email gate")
    void signupTokenNeedsNoRelogin() throws Exception {
        String token = bearer(signup("Alok Kumar", alokEmail));

        MvcResult result = mvc.perform(get("/api/v1/members").header("Authorization", token))
                .andExpect(status().isNotFound())
                .andReturn();

        assertThat(json.readTree(result.getResponse().getContentAsString()).get("code").asString())
                .isEqualTo("NOT_A_MEMBER");
    }

    /**
     * And the workspace is real, not held.
     *
     * <p>The case a flag that only relaxed {@code SecurityConfig} would have missed:
     * {@code OnboardingService} reads {@code user.isEmailVerified()} directly, so an unverified user's
     * create returns 202 with the workspace <i>held</i> and nothing written. 201 here says both gates are
     * satisfied, because the user genuinely is verified.
     */
    @Test
    @DisplayName("the workspace is created outright, not held")
    void workspaceIsCreatedNotHeld() throws Exception {
        String token = bearer(signup("Alok Kumar", alokEmail));

        mvc.perform(post("/api/v1/onboarding/workspace")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"NextWebSpark Search","companySize":"11-50 people",
                                 "primaryRegion":"GCC","teamFocus":"Executive search"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workspace.id").exists());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private JsonNode signup(String name, String emailAddress) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"%s","email":"%s","password":"%s","termsAccepted":true}
                                """.formatted(name, emailAddress, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();

        return json.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(JsonNode session) {
        return "Bearer " + session.get("accessToken").asString();
    }
}
