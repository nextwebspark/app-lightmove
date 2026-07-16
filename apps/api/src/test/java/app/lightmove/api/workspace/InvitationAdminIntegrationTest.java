package app.lightmove.api.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** Outstanding invitations as an admin manages them: list, revoke (kills the link), resend (rotates it). */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class InvitationAdminIntegrationTest extends FlowTestSupport {

    @Test
    @DisplayName("the invitation list is the admin's; a consultant is refused")
    void listIsAdminOnly() throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Invite Firm");
        String admin = login(alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "CONSULTANT");
        invite(admin, "omar@" + domain, "RESEARCHER");

        mvc.perform(get("/api/v1/invitations").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("omar@" + domain))
                .andExpect(jsonPath("$[0].invitedByName").value("Alok Kumar"));

        mvc.perform(get("/api/v1/invitations").header("Authorization", "Bearer " + login(sara)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("revoking an invitation kills the emailed link")
    void revokeKillsTheLink() throws Exception {
        String alok = "alok@" + domain;
        String omar = "omar@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Revoke Firm");
        String admin = login(alok);
        invite(admin, omar, "CONSULTANT");
        String linkToken = email.latestTokenFor(omar);
        String invitationId = onlyInvitationId(admin);

        mvc.perform(delete("/api/v1/invitations/" + invitationId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        // Omar signs up verified and presents the link he was mailed. It is dead.
        String omarToken = verifiedUser("Omar Khalil", omar);
        MvcResult refused = mvc.perform(post("/api/v1/onboarding/invitations/accept")
                        .header("Authorization", "Bearer " + omarToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}
                                """.formatted(linkToken)))
                .andReturn();
        assertThat(codeOf(refused)).isEqualTo("INVITATION_INVALID");

        // And a fresh invitation can follow a revocation — the partial index only holds one PENDING.
        invite(admin, omar, "CONSULTANT");
    }

    @Test
    @DisplayName("resending rotates the token: the old link dies, the new one admits")
    void resendRotatesTheToken() throws Exception {
        String alok = "alok@" + domain;
        String omar = "omar@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Resend Firm");
        String admin = login(alok);
        invite(admin, omar, "CONSULTANT");
        String oldLink = email.latestTokenFor(omar);

        mvc.perform(post("/api/v1/invitations/" + onlyInvitationId(admin) + "/resend")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        String newLink = email.latestTokenFor(omar);
        assertThat(newLink).isNotEqualTo(oldLink);

        String omarToken = verifiedUser("Omar Khalil", omar);

        MvcResult stale = mvc.perform(post("/api/v1/onboarding/invitations/accept")
                        .header("Authorization", "Bearer " + omarToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}
                                """.formatted(oldLink)))
                .andReturn();
        assertThat(codeOf(stale)).isEqualTo("INVITATION_INVALID");

        mvc.perform(post("/api/v1/onboarding/invitations/accept")
                        .header("Authorization", "Bearer " + omarToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}
                                """.formatted(newLink)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("another firm's invitation ids do not exist as far as this admin is concerned")
    void crossWorkspaceInvitationProbeIs404() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Probe Target Firm");
        String admin = login(alok);
        invite(admin, "omar@" + domain, "CONSULTANT");
        String invitationId = onlyInvitationId(admin);

        String rivalEmail = "boss@rival-" + domain;
        createWorkspace(verifiedUser("Rival Boss", rivalEmail), "Probe Rival Firm");
        String rival = login(rivalEmail);

        mvc.perform(delete("/api/v1/invitations/" + invitationId)
                        .header("Authorization", "Bearer " + rival))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/invitations/" + invitationId + "/resend")
                        .header("Authorization", "Bearer " + rival))
                .andExpect(status().isNotFound());
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

    private String onlyInvitationId(String adminToken) throws Exception {
        return body(mvc.perform(get("/api/v1/invitations")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()).get(0).get("id").asText();
    }
}
