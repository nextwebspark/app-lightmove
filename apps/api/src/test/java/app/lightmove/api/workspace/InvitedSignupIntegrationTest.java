package app.lightmove.api.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import app.lightmove.api.core.security.rbac.RbacService;
import app.lightmove.api.core.security.rbac.WorkspaceRole;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.core.security.token.Tokens;
import app.lightmove.api.workspace.model.Invitation;
import app.lightmove.api.workspace.repository.InvitationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/**
 * The one-shot invited signup: an invitee with no account sets a password on the accept screen and is
 * ACTIVE in the workspace immediately, with no separate email-verification step.
 *
 * <p>The invitation token, mailed only to the invited address, is the mailbox proof verification would
 * otherwise establish — so the account is created already verified, and its address is taken from the
 * invitation, never from the request. An address that already has an account is turned away rather than
 * overwritten.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class InvitedSignupIntegrationTest extends FlowTestSupport {

    @Autowired InvitationRepository invitationRepo;
    @Autowired UserRepository userRepo;
    @Autowired RbacService rbac;

    @Test
    @DisplayName("an invitee sets a password and lands ACTIVE — no verification email, first session already works")
    void invitedSignupLandsActiveWithoutVerifying() throws Exception {
        String alok = "alok@" + domain;
        String omar = "omar@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "One Shot Firm");
        invite(login(alok), omar, "MEMBER");

        String token = email.latestTokenFor(omar);
        email.clear(); // anything sent from here on is the accept's own doing

        MvcResult result = acceptSignup(token, "Omar Khalil", PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(jsonPath("$.user.emailVerified").value(true))
                .andExpect(jsonPath("$.user.workspace.name").value("One Shot Firm"))
                .andExpect(jsonPath("$.user.workspace.roles[0]").value("MEMBER"))
                .andReturn();

        // The invite prompted no verification email, and accepting sent none either.
        assertThat(email.sent()).isEmpty();

        // The very first session already carries the workspace claim and SCOPE_VERIFIED, so a
        // membership-gated route admits it with no second login and no refresh.
        String access = body(result).at("/accessToken").asText();
        mvc.perform(get("/api/v1/members").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("an address that already has an account is sent to log in, not duplicated into the workspace")
    void existingAccountIsRefusedNotDuplicated() throws Exception {
        String alok = "alok@" + domain;
        String omar = "omar@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Dup Firm");
        invite(login(alok), omar, "MEMBER");
        String token = email.latestTokenFor(omar);

        // Omar already has an account (unverified is beside the point — the address is taken).
        signup("Omar Khalil", omar);

        MvcResult refused = acceptSignup(token, "Omar Khalil", PASSWORD).andReturn();
        assertThat(refused.getResponse().getStatus()).isEqualTo(409);
        assertThat(codeOf(refused)).isEqualTo("EMAIL_ALREADY_REGISTERED");

        // No membership was minted: the roster still holds only its admin.
        JsonNode roster = body(mvc.perform(get("/api/v1/members")
                        .header("Authorization", "Bearer " + login(alok)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(roster.size()).isEqualTo(1);
        assertThat(roster.toString()).doesNotContain(omar);
    }

    @Test
    @DisplayName("only a live token works, and a consumed one cannot be reused")
    void tokenMustBeLiveAndSingleUse() throws Exception {
        String alok = "alok@" + domain;
        String omar = "omar@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Single Use Firm");
        invite(login(alok), omar, "MEMBER");
        String token = email.latestTokenFor(omar);

        // A token that never existed.
        MvcResult bogus = acceptSignup("not-a-real-token", "Nobody", PASSWORD).andReturn();
        assertThat(codeOf(bogus)).isEqualTo("INVITATION_INVALID");

        // The real one admits exactly once; presenting it again finds an invitation already consumed.
        acceptSignup(token, "Omar Khalil", PASSWORD).andExpect(status().isCreated());
        MvcResult reused = acceptSignup(token, "Omar Again", PASSWORD).andReturn();
        assertThat(codeOf(reused)).isEqualTo("INVITATION_INVALID");
    }

    @Test
    @DisplayName("a weak password is refused and leaves nothing behind — the same token still works after")
    void weakPasswordRollsBackCleanly() throws Exception {
        String alok = "alok@" + domain;
        String omar = "omar@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Rollback Firm");
        invite(login(alok), omar, "MEMBER");
        String token = email.latestTokenFor(omar);

        // 73 characters with a digit: the DTO's @Size(min) and @Pattern both pass, so this reaches the
        // service transaction, where PasswordPolicy's 72-char ceiling rejects it. The failure this test
        // names lives inside the transaction, not at the controller boundary.
        String tooLong = "a".repeat(72) + "1";
        MvcResult weak = acceptSignup(token, "Omar Khalil", tooLong).andReturn();
        assertThat(codeOf(weak)).isEqualTo("VALIDATION_FAILED");

        // Nothing was created and the invitation was not consumed: the same token, with a good password,
        // still admits.
        acceptSignup(token, "Omar Khalil", PASSWORD).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("a lapsed token is refused as expired, told apart from an unknown one")
    void expiredTokenIsRefused() throws Exception {
        String alok = "alok@" + domain;
        String omar = "omar@" + domain;
        String workspaceId = createWorkspace(verifiedUser("Alok Kumar", alok), "Expiry Firm");
        UUID inviterId = userRepo.findByEmail(alok).orElseThrow().getId();

        // Persisted straight and already an hour past expiry — the API only ever issues live invitations,
        // so there is no endpoint that would mint an expired one to test against.
        String plaintext = Tokens.generate();
        invitationRepo.save(Invitation.create(UUID.fromString(workspaceId), omar,
                rbac.role(WorkspaceRole.MEMBER), Tokens.hash(plaintext), inviterId,
                Instant.now().minus(Duration.ofHours(1))));

        MvcResult expired = acceptSignup(plaintext, "Omar Khalil", PASSWORD).andReturn();
        assertThat(codeOf(expired)).isEqualTo("INVITATION_EXPIRED");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** No Authorization header — the invitee has no session, which is the whole point of this endpoint. */
    private ResultActions acceptSignup(String token, String fullName, String password) throws Exception {
        return mvc.perform(post("/api/v1/onboarding/accept-invitation-signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token":"%s","fullName":"%s","password":"%s"}
                        """.formatted(token, fullName, password)));
    }

    private void invite(String adminToken, String inviteeEmail, String role) throws Exception {
        mvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"email":"%s","role":"%s"}]
                                """.formatted(inviteeEmail, role)))
                .andExpect(status().isOk());
    }
}
