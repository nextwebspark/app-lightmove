package app.lightmove.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
// Spring Boot 4 ships Jackson 3, whose packages moved from com.fasterxml.* to tools.jackson.*.
// The old com.fasterxml jars are still on the classpath transitively, and importing from them
// compiles fine — then fails at runtime with "no ObjectMapper bean", because the one Spring
// actually registers is this one.
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The whole signup-to-workspace journey, against a real Postgres.
 *
 * <p>Two routes in, and only two: create a workspace, or accept an invitation. There is no join
 * request and no approval queue — an admin naming someone <i>is</i> the approval.
 *
 * <p>This is the suite that replaced an ad-hoc curl script. The script needed the shared Cloud SQL
 * database wiped between runs, because it left users behind and then collided with them. These tests
 * get a fresh container, so they are repeatable by construction and never touch a shared database.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class AuthFlowIntegrationTest {

    private static final String PASSWORD = "secret123";

    /** Distinguishes one test's fixtures from another's. See {@link #reset()}. */
    private static final AtomicInteger RUN = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RecordingEmailSender email;

    private String domain;
    private String alokEmail;
    private String saraEmail;

    /**
     * Every test gets its own email domain, and therefore its own users and its own workspaces.
     *
     * <p>The Postgres container is shared across the class and nothing rolls back — MockMvc requests
     * commit, and the audit writes are {@code REQUIRES_NEW} on another thread, so a transactional test
     * would not contain them anyway. Rather than fight that with truncation (which the append-only
     * trigger on the audit table would refuse), each test simply works in a namespace of its own.
     * Tests then cannot see each other's data, in any order, run in parallel or not.
     */
    @BeforeEach
    void reset() {
        email.clear();
        domain = "firm%d.example".formatted(RUN.incrementAndGet());
        alokEmail = "alok@" + domain;
        saraEmail = "sara@" + domain;
    }

    // ── Signup gates ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("a consumer email address cannot sign up")
    void rejectsConsumerEmail() throws Exception {
        MvcResult result = signupRaw("Someone", "someone@gmail.com", PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(result)).isEqualTo("EMAIL_NOT_WORK_ADDRESS");
    }

    @Test
    @DisplayName("a disposable inbox cannot sign up")
    void rejectsDisposableEmail() throws Exception {
        assertThat(codeOf(signupRaw("Someone", "someone@mailinator.com", PASSWORD)))
                .isEqualTo("EMAIL_DISPOSABLE");
    }

    @Test
    @DisplayName("the same email cannot be registered twice")
    void rejectsDuplicateEmail() throws Exception {
        signup("Alok Kumar", alokEmail, PASSWORD);

        assertThat(codeOf(signupRaw("Impostor", alokEmail, PASSWORD)))
                .isEqualTo("EMAIL_ALREADY_REGISTERED");
    }

    // ── Verification gates access ─────────────────────────────────────────────

    @Test
    @DisplayName("signup yields a session but no workspace, and no access until verified")
    void signupIsUnverifiedAndWorkspaceless() throws Exception {
        JsonNode session = signup("Alok Kumar", alokEmail, PASSWORD);

        assertThat(session.at("/user/emailVerified").asBoolean()).isFalse();
        // Null or absent — either way there is no workspace. Asserting on the id rather than the
        // node keeps this honest whichever way Jackson chooses to represent "nothing".
        assertThat(session.at("/user/workspace/id").asString("")).isEmpty();

        // The gate that makes email verification mean something: an unverified user holds a valid
        // token and still cannot read a single row of workspace data.
        mvc.perform(get("/api/v1/members").header("Authorization", bearer(session)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("clicking the emailed link verifies the account")
    void verificationLinkWorks() throws Exception {
        signup("Alok Kumar", alokEmail, PASSWORD);

        // Read the token straight out of the email we "sent" — the real link, not a fixture.
        String token = email.latestTokenFor(alokEmail);

        MvcResult result = mvc.perform(post("/api/v1/auth/verify").param("token", token))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(body(result).get("emailVerified").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("a verification token cannot be redeemed twice")
    void verificationTokenIsSingleUse() throws Exception {
        signup("Alok Kumar", alokEmail, PASSWORD);
        String token = email.latestTokenFor(alokEmail);

        mvc.perform(post("/api/v1/auth/verify").param("token", token)).andExpect(status().isOk());

        MvcResult replay = mvc.perform(post("/api/v1/auth/verify").param("token", token)).andReturn();
        assertThat(codeOf(replay)).isEqualTo("TOKEN_INVALID");
    }

    // ── Workspace creation ────────────────────────────────────────────────────

    @Test
    @DisplayName("the workspace creator becomes its ADMIN")
    void createWorkspaceMakesCreatorAdmin() throws Exception {
        String token = verifiedUser("Alok Kumar", alokEmail);

        MvcResult result = mvc.perform(post("/api/v1/onboarding/workspace")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"NextWebSpark Search","companySize":"11-50 people",
                                 "primaryRegion":"GCC","jobTitle":"Partner","teamFocus":"Executive search"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode user = body(result);
        assertThat(user.at("/workspace/name").asText()).isEqualTo("NextWebSpark Search");
        assertThat(user.at("/workspace/roles/0").asText()).isEqualTo("ADMIN");
        assertThat(user.at("/workspace/emailDomain").asText()).isEqualTo(domain);

        // Derived from the name, not supplied by the client. `startsWith` rather than equals, because
        // other tests in this class create a workspace of the same name against the same container, and
        // the generator suffixes a collision to -2, -3… That suffixing is itself the behaviour under
        // test here: two firms may share a name, and they must not share a URL.
        assertThat(user.at("/workspace/slug").asText()).startsWith("nextwebspark-search");
    }

    // ── Verification gates the claim to a company domain ──────────────────────

    /**
     * The trust model in one test.
     *
     * <p>Membership of a firm is inferred from an email domain, so an unverified address is an unproven
     * claim and must buy nothing. Anyone can sign up as {@code victim@realfirm.com}; nobody may become
     * ADMIN of a workspace bound to {@code realfirm.com} by doing so.
     *
     * <p>Note what is asserted, and what is not. The request is <b>accepted</b> — refusing it stranded a
     * user in the middle of their own signup, which is what this used to do. What must not happen is
     * that anything comes into <i>existence</i>: no workspace, only a held wizard the response reports
     * as {@code onboardingHeld}.
     */
    @Test
    @DisplayName("an unverified user's workspace does not exist until they verify")
    void unverifiedUserCannotClaimADomain() throws Exception {
        // Signed up, never clicked the link. The token is valid; the address is not proven.
        String unverified = bearer(signup("Impostor", "impostor@" + domain, PASSWORD));

        // Accepted, not refused: they may finish their own wizard.
        mvc.perform(post("/api/v1/onboarding/workspace")
                        .header("Authorization", unverified)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Squatted Firm","companySize":"11-50 people","primaryRegion":"GCC",
                                 "jobTitle":"Partner","teamFocus":"Executive search"}
                                """))
                .andExpect(status().isAccepted())
                // No workspace on the response, because there is no workspace — only a held wizard.
                .andExpect(jsonPath("$.workspace").doesNotExist())
                .andExpect(jsonPath("$.onboardingHeld").value(true));
    }

    /** And it comes into existence the moment they prove the mailbox — not before, and not never. */
    @Test
    @DisplayName("verifying materialises the workspace the wizard was holding")
    void verifyingMaterialisesTheHeldWorkspace() throws Exception {
        String unverified = bearer(signup("Alok Kumar", alokEmail, PASSWORD));

        mvc.perform(post("/api/v1/onboarding/workspace")
                        .header("Authorization", unverified)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"NextWebSpark Search","companySize":"11-50 people",
                                 "primaryRegion":"GCC","jobTitle":"Partner","teamFocus":"Executive search"}
                                """))
                .andExpect(status().isAccepted());

        MvcResult verified = mvc.perform(post("/api/v1/auth/verify")
                        .param("token", email.latestTokenFor(alokEmail)))
                .andExpect(status().isOk())
                .andReturn();

        // The click that verified the email is the click that created the organisation.
        JsonNode user = body(verified);
        assertThat(user.get("emailVerified").asBoolean()).isTrue();
        assertThat(user.at("/workspace/name").asText()).isEqualTo("NextWebSpark Search");
        assertThat(user.at("/workspace/roles/0").asText()).isEqualTo("ADMIN");
        assertThat(user.at("/workspace/emailDomain").asText()).isEqualTo(domain);
    }

    /**
     * Verifying straight from the inbox, before filling in the wizard, must strand nobody: no
     * workspace, nothing held, no invitation — the create form is where they belong, and it works.
     */
    @Test
    @DisplayName("verifying without a held wizard leaves the user free to create a workspace")
    void verifyingWithoutAWizardLeavesTheUserFree() throws Exception {
        signup("Alok Kumar", alokEmail, PASSWORD);
        MvcResult verified = mvc.perform(post("/api/v1/auth/verify")
                        .param("token", email.latestTokenFor(alokEmail)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode user = body(verified);
        assertThat(user.get("emailVerified").asBoolean()).isTrue();
        assertThat(user.at("/workspace/id").asString("")).isEmpty();
        assertThat(user.get("onboardingHeld").asBoolean()).isFalse();
        assertThat(user.at("/pendingInvitation/workspaceName").asString("")).isEmpty();

        // And the proof that it is not merely cosmetic: they can still create the workspace.
        createWorkspace(login(alokEmail), "NextWebSpark Search");
    }

    /**
     * Going back a step in the wizard.
     *
     * <p>Step 2 commits — the workspace is real the moment it is created. So "Back" cannot mean "create
     * one", it means "correct the one you made", and without an edit endpoint the Back button the mockup
     * draws could only ever produce "you already have a workspace".
     */
    @Test
    @DisplayName("an admin can correct the workspace they created, without creating a second one")
    void adminCanEditTheirWorkspace() throws Exception {
        String alok = verifiedUser("Alok Kumar", alokEmail);
        createWorkspace(alok, "NextWebSpark Search");
        String admin = tokenWithWorkspace(alokEmail);

        MvcResult edited = mvc.perform(patch("/api/v1/onboarding/workspace")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"NextWebSpark Executive","companySize":"51-200 people",
                                 "primaryRegion":"MENA","jobTitle":"Partner","teamFocus":"Board advisory"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(body(edited).at("/workspace/name").asText()).isEqualTo("NextWebSpark Executive");

        // Renaming must not re-identify it: the slug is in URLs and in anything anyone bookmarked.
        assertThat(body(edited).at("/workspace/slug").asText()).startsWith("nextwebspark-search");
    }

    @Test
    @DisplayName("a member who is not an admin cannot edit the workspace")
    void nonAdminCannotEditTheWorkspace() throws Exception {
        String alok = verifiedUser("Alok Kumar", alokEmail);
        createWorkspace(alok, "NextWebSpark Search");
        String admin = tokenWithWorkspace(alokEmail);
        inviteAndAccept(admin, saraEmail, "MEMBER");

        mvc.perform(patch("/api/v1/onboarding/workspace")
                        .header("Authorization", "Bearer " + tokenWithWorkspace(saraEmail))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Sara's Firm Now","companySize":"1-10 people","primaryRegion":"GCC",
                                 "jobTitle":"Consultant","teamFocus":"Executive search"}
                                """))
                .andExpect(status().isForbidden());
    }

    // ── The invitation flow — the only way into an existing workspace ─────────

    /**
     * An admin naming a colleague <i>is</i> the approval, made up front — so an invitee lands ACTIVE
     * with the role the admin chose, immediately. The role is the admin's, not the invitee's: nothing
     * in this flow ever asks them what they would like to be.
     */
    @Test
    @DisplayName("an invited colleague joins immediately, as the role the admin chose")
    void invitedColleagueLandsActiveImmediately() throws Exception {
        String alok = verifiedUser("Alok Kumar", alokEmail);
        createWorkspace(alok, "NextWebSpark Search");
        String admin = tokenWithWorkspace(alokEmail);

        mvc.perform(post("/api/v1/onboarding/invitations")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"email":"%s","role":"MEMBER"}]
                                """.formatted(saraEmail)))
                .andExpect(status().isOk());

        String inviteToken = email.latestTokenFor(saraEmail);

        // She accepts it as herself: signed up, and with the mailbox proven.
        String sara = verifiedUser("Sara Al-Mansour", saraEmail);

        MvcResult accepted = mvc.perform(post("/api/v1/onboarding/invitations/accept")
                        .header("Authorization", "Bearer " + sara)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}
                                """.formatted(inviteToken)))
                .andExpect(status().isOk())
                .andReturn();

        // In, immediately, as a MEMBER — no admin had to act a second time.
        JsonNode user = body(accepted);
        assertThat(user.at("/workspace/name").asText()).isEqualTo("NextWebSpark Search");
        assertThat(user.at("/workspace/roles/0").asText()).isEqualTo("MEMBER");
    }

    /**
     * The server-derived invitee marker. The emailed token lives in one browser tab's sessionStorage;
     * an invitee who verifies in a fresh tab still has to be routed to "join {workspace}" and not into
     * create-your-own — so {@code /me} carries the invitation, from the database, in every tab.
     */
    @Test
    @DisplayName("an invited, unplaced user sees their invitation on /me — and it clears once placed")
    void pendingInvitationSurfacesOnMe() throws Exception {
        String alok = verifiedUser("Alok Kumar", alokEmail);
        createWorkspace(alok, "NextWebSpark Search");
        invite(tokenWithWorkspace(alokEmail), saraEmail, "MEMBER");

        String sara = verifiedUser("Sara Al-Mansour", saraEmail);

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + sara))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspace").doesNotExist())
                .andExpect(jsonPath("$.pendingInvitation.workspaceName").value("NextWebSpark Search"))
                .andExpect(jsonPath("$.pendingInvitation.role").value("MEMBER"));

        mvc.perform(post("/api/v1/onboarding/accept-invitation")
                        .header("Authorization", "Bearer " + sara))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + sara))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspace.name").value("NextWebSpark Search"))
                .andExpect(jsonPath("$.pendingInvitation").doesNotExist());
    }

    /**
     * The token-less accept. The emailed token only ever proved control of the invited mailbox — and a
     * verified session whose address matches the invitation has proven exactly that. Verification
     * subsumes the token; the guards (matching address, verified, unplaced) are the same ones the
     * token path applies.
     */
    @Test
    @DisplayName("a verified invitee can accept without the token, exactly once")
    void tokenLessAcceptLandsTheInvitee() throws Exception {
        String alok = verifiedUser("Alok Kumar", alokEmail);
        createWorkspace(alok, "NextWebSpark Search");
        invite(tokenWithWorkspace(alokEmail), saraEmail, "MEMBER");

        String sara = verifiedUser("Sara Al-Mansour", saraEmail);

        MvcResult accepted = mvc.perform(post("/api/v1/onboarding/accept-invitation")
                        .header("Authorization", "Bearer " + sara))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(body(accepted).at("/workspace/roles/0").asText()).isEqualTo("MEMBER");

        // Consumed. The invitation went ACCEPTED with the first redeem, so there is no pending
        // invitation left to find — the second call has nothing to redeem.
        MvcResult again = mvc.perform(post("/api/v1/onboarding/accept-invitation")
                        .header("Authorization", "Bearer " + sara))
                .andReturn();
        assertThat(codeOf(again)).isEqualTo("INVITATION_INVALID");
    }

    @Test
    @DisplayName("the token-less accept is refused while the address is unproven")
    void tokenLessAcceptRequiresVerification() throws Exception {
        String alok = verifiedUser("Alok Kumar", alokEmail);
        createWorkspace(alok, "NextWebSpark Search");
        invite(tokenWithWorkspace(alokEmail), saraEmail, "MEMBER");

        // Sara signed up but never clicked her link. Her session is real; her address is a claim.
        String unverified = signup("Sara Al-Mansour", saraEmail, PASSWORD).get("accessToken").asText();

        mvc.perform(post("/api/v1/onboarding/accept-invitation")
                        .header("Authorization", "Bearer " + unverified))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("with no outstanding invitation, the token-less accept has nothing to redeem")
    void tokenLessAcceptWithoutInvitationIsRefused() throws Exception {
        String nobody = verifiedUser("No Invite", "noinvite@" + domain);

        MvcResult refused = mvc.perform(post("/api/v1/onboarding/accept-invitation")
                        .header("Authorization", "Bearer " + nobody))
                .andReturn();
        assertThat(codeOf(refused)).isEqualTo("INVITATION_INVALID");
    }

    @Test
    @DisplayName("an invitation with no role lands the invitee as a MEMBER")
    void inviteRoleDefaultsToMember() throws Exception {
        String alok = verifiedUser("Alok Kumar", alokEmail);
        createWorkspace(alok, "NextWebSpark Search");

        mvc.perform(post("/api/v1/onboarding/invitations")
                        .header("Authorization", "Bearer " + tokenWithWorkspace(alokEmail))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"email":"%s"}]
                                """.formatted(saraEmail)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/onboarding/invitations/preview")
                        .param("token", email.latestTokenFor(saraEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"));
    }

    /**
     * An invitation is addressed to a person. Forwarding the link must not admit whoever it was
     * forwarded to — otherwise "invite one colleague" quietly means "invite anyone they know".
     */
    @Test
    @DisplayName("a forwarded invitation does not admit somebody else")
    void forwardedInvitationIsRefused() throws Exception {
        String alok = verifiedUser("Alok Kumar", alokEmail);
        createWorkspace(alok, "NextWebSpark Search");
        invite(tokenWithWorkspace(alokEmail), saraEmail, "MEMBER");

        String inviteToken = email.latestTokenFor(saraEmail);

        // Omar has the link — Sara forwarded it, or he read it over her shoulder. He is not Sara.
        String omar = verifiedUser("Omar Khalil", "omar@" + domain);

        mvc.perform(post("/api/v1/onboarding/invitations/accept")
                        .header("Authorization", "Bearer " + omar)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}
                                """.formatted(inviteToken)))
                .andExpect(status().isBadRequest());
    }

    /**
     * The preview is what makes the flow usable at all: the invitee has no account when they click, so
     * the SPA must be able to tell them what they were invited to before anyone is authenticated.
     */
    @Test
    @DisplayName("an invitation can be read, unauthenticated, from its link alone")
    void invitationPreviewIsReadableAnonymously() throws Exception {
        String alok = verifiedUser("Alok Kumar", alokEmail);
        createWorkspace(alok, "NextWebSpark Search");
        invite(tokenWithWorkspace(alokEmail), saraEmail, "MEMBER");

        // No Authorization header at all — this is a stranger with a link.
        MvcResult preview = mvc.perform(get("/api/v1/onboarding/invitations/preview")
                        .param("token", email.latestTokenFor(saraEmail)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode invitation = body(preview);
        assertThat(invitation.get("email").asText()).isEqualTo(saraEmail);
        assertThat(invitation.get("role").asText()).isEqualTo("MEMBER");
        assertThat(invitation.get("workspaceName").asText()).isEqualTo("NextWebSpark Search");
        assertThat(invitation.get("inviterName").asText()).isEqualTo("Alok Kumar");
    }

    @Test
    @DisplayName("a garbage invitation token is refused, and says nothing about why")
    void unknownInvitationTokenIsRefused() throws Exception {
        mvc.perform(get("/api/v1/onboarding/invitations/preview")
                        .param("token", "not-a-real-token"))
                .andExpect(status().isBadRequest());
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a wrong password and an unknown account are indistinguishable to the caller")
    void loginDoesNotLeakAccountExistence() throws Exception {
        signup("Alok Kumar", alokEmail, PASSWORD);

        MvcResult wrongPassword = loginRaw(alokEmail, "wrongpassword1");
        MvcResult noSuchUser = loginRaw("ghost@" + domain, "wrongpassword1");

        // Same status, same code, same message. Anything else is an account-enumeration oracle: feed in
        // a leaked email list and learn which addresses are LightMove customers.
        assertThat(wrongPassword.getResponse().getStatus())
                .isEqualTo(noSuchUser.getResponse().getStatus());
        assertThat(codeOf(wrongPassword)).isEqualTo(codeOf(noSuchUser)).isEqualTo("INVALID_CREDENTIALS");
        assertThat(body(wrongPassword).get("detail")).isEqualTo(body(noSuchUser).get("detail"));
    }

    @Test
    @DisplayName("repeated failures lock the account")
    void lockoutAfterRepeatedFailures() throws Exception {
        signup("Alok Kumar", alokEmail, PASSWORD);

        // The configured threshold is 5.
        for (int attempt = 0; attempt < 5; attempt++) {
            loginRaw(alokEmail, "wrongpassword1");
        }

        // Even the *correct* password is now refused — a lockout that yields to the right password
        // would be no lockout at all.
        MvcResult locked = loginRaw(alokEmail, PASSWORD);
        assertThat(codeOf(locked)).isEqualTo("ACCOUNT_LOCKED");
    }

    // ── Refresh token rotation and theft detection ────────────────────────────

    @Test
    @DisplayName("refreshing rotates the token, and replaying the old one kills the whole session")
    void refreshTokenReuseRevokesTheFamily() throws Exception {
        signup("Alok Kumar", alokEmail, PASSWORD);
        Cookie original = refreshCookie(loginRaw(alokEmail, PASSWORD));

        // A normal refresh. The token we presented is now spent, and we hold its successor.
        MvcResult rotated = mvc.perform(post("/api/v1/auth/refresh").cookie(original).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        Cookie successor = refreshCookie(rotated);
        assertThat(successor.getValue()).isNotEqualTo(original.getValue());

        // Now replay the spent token — this is what a thief with a stolen cookie does.
        MvcResult replay = mvc.perform(post("/api/v1/auth/refresh").cookie(original).with(csrf()))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertThat(codeOf(replay)).isEqualTo("REFRESH_TOKEN_REUSED");

        // And the whole family died with it. The legitimate client's *valid* successor is dead too.
        // We cannot tell victim from thief, so both are made to sign in again — losing a session is an
        // acceptable price; leaving a thief with a working token is not.
        //
        // The victim also gets REUSED rather than a plain INVALID, and that is not a bug worth
        // dressing up: their token was revoked *because* of a replay, and from the server's side a
        // revoked token being presented is a revoked token being presented. Both are 401, both mean
        // "sign in again", and inventing a distinction would only tell an attacker which of the two
        // ends of a compromised session they are holding.
        MvcResult victim = mvc.perform(post("/api/v1/auth/refresh").cookie(successor).with(csrf()))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertThat(codeOf(victim)).isEqualTo("REFRESH_TOKEN_REUSED");
    }

    /**
     * A rejected refresh token is dead, and the response says so by clearing the cookie.
     *
     * <p>Without this the browser keeps the corpse and presents it again on every single page load —
     * and each presentation of a revoked token is, by definition, reuse. One genuine detection becomes
     * an endless stream of TOKEN_REUSE_DETECTED events, each revoking nothing, until the security log is
     * mostly noise about an attack that happened once. A log nobody trusts is a log nobody reads.
     */
    @Test
    @DisplayName("a rejected refresh clears the cookie, so the browser stops presenting a dead token")
    void rejectedRefreshClearsTheCookie() throws Exception {
        signup("Alok Kumar", alokEmail, PASSWORD);
        Cookie cookie = refreshCookie(loginRaw(alokEmail, PASSWORD));

        // Kill it the honest way: log out. The cookie in the browser is now worthless.
        mvc.perform(post("/api/v1/auth/logout").cookie(cookie).with(csrf()))
                .andExpect(status().isNoContent());

        MvcResult rejected = mvc.perform(post("/api/v1/auth/refresh").cookie(cookie).with(csrf()))
                .andExpect(status().isUnauthorized())
                .andReturn();

        Cookie cleared = rejected.getResponse().getCookie("lm_refresh");
        assertThat(cleared)
                .as("the 401 must carry a Set-Cookie that expires the dead token")
                .isNotNull();
        assertThat(cleared.getMaxAge())
                .as("Max-Age=0 is what tells the browser to drop it")
                .isZero();
    }

    @Test
    @DisplayName("logging out revokes the refresh token")
    void logoutRevokesTheSession() throws Exception {
        signup("Alok Kumar", alokEmail, PASSWORD);
        Cookie cookie = refreshCookie(loginRaw(alokEmail, PASSWORD));

        mvc.perform(post("/api/v1/auth/logout").cookie(cookie).with(csrf()))
                .andExpect(status().isNoContent());

        MvcResult afterLogout = mvc.perform(post("/api/v1/auth/refresh").cookie(cookie).with(csrf()))
                .andReturn();
        assertThat(afterLogout.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("the refresh endpoint is CSRF-protected")
    void refreshRequiresCsrf() throws Exception {
        signup("Alok Kumar", alokEmail, PASSWORD);
        Cookie cookie = refreshCookie(loginRaw(alokEmail, PASSWORD));

        // The cookie alone is not enough. A browser would attach it automatically on a request forged by
        // another site, and without the matching double-submit header that request must fail.
        mvc.perform(post("/api/v1/auth/refresh").cookie(cookie))
                .andExpect(status().isForbidden());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private JsonNode signup(String name, String emailAddress, String password) throws Exception {
        return body(signupRaw(name, emailAddress, password));
    }

    private MvcResult signupRaw(String name, String emailAddress, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"%s","email":"%s","password":"%s","termsAccepted":true}
                                """.formatted(name, emailAddress, password)))
                .andReturn();
    }

    /** Signs up, clicks the emailed link, and returns a bearer token that says "verified". */
    private String verifiedUser(String name, String emailAddress) throws Exception {
        signup(name, emailAddress, PASSWORD);
        mvc.perform(post("/api/v1/auth/verify").param("token", email.latestTokenFor(emailAddress)))
                .andExpect(status().isOk());

        // The token from signup still claims unverified — it was minted before the click. Logging in
        // again mints one that carries the truth.
        return login(emailAddress);
    }

    private String login(String emailAddress) throws Exception {
        MvcResult result = loginRaw(emailAddress, PASSWORD);
        JsonNode body = body(result);

        // Fail with the server's own words rather than a NullPointerException three frames later.
        assertThat(body.get("accessToken"))
                .as("login for %s failed: %s", emailAddress, body)
                .isNotNull();

        return body.get("accessToken").asText();
    }

    private MvcResult loginRaw(String emailAddress, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(emailAddress, password)))
                .andReturn();
    }

    /** @return the new workspace's id. */
    private String createWorkspace(String bearerToken, String name) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/onboarding/workspace")
                        .header("Authorization", "Bearer " + bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","companySize":"11-50 people","primaryRegion":"GCC",
                                 "jobTitle":"Partner","teamFocus":"Executive search"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();

        return body(result).at("/workspace/id").asText();
    }

    /**
     * A bearer token that actually carries the new workspace claim.
     *
     * <p>Creating a workspace does not retro-fit the token the caller is already holding — that one was
     * minted before the workspace existed and says the user has none. A real client calls
     * {@code /auth/refresh} at this point; the test signs in again, which mints the same fresh claims.
     */
    private String tokenWithWorkspace(String emailAddress) throws Exception {
        return login(emailAddress);
    }

    private void invite(String adminToken, String inviteeEmail, String role) throws Exception {
        mvc.perform(post("/api/v1/onboarding/invitations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"email":"%s","role":"%s"}]
                                """.formatted(inviteeEmail, role)))
                .andExpect(status().isOk());
    }

    private void inviteAndAccept(String adminToken, String inviteeEmail, String role) throws Exception {
        invite(adminToken, inviteeEmail, role);
        String token = email.latestTokenFor(inviteeEmail);
        String invitee = verifiedUser("Invitee " + inviteeEmail, inviteeEmail);
        mvc.perform(post("/api/v1/onboarding/invitations/accept")
                        .header("Authorization", "Bearer " + invitee)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}
                                """.formatted(token)))
                .andExpect(status().isOk());
    }

    private Cookie refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("lm_refresh");
        assertThat(cookie).as("refresh cookie should be set").isNotNull();
        return cookie;
    }

    private static String bearer(JsonNode session) {
        return "Bearer " + session.get("accessToken").asText();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private String codeOf(MvcResult result) throws Exception {
        JsonNode node = body(result).get("code");
        return node == null ? null : node.asText();
    }
}
