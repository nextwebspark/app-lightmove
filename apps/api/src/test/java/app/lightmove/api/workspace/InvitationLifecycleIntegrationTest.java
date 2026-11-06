package app.lightmove.api.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/**
 * An invitation from being sent to being redeemed or withdrawn: who is shown it, what it costs, whose approval it
 * carries, and what happens to it when the people around it change.
 */
@IntegrationTest
class InvitationLifecycleIntegrationTest extends FlowTestSupport {

    @Autowired @FixtureJdbc JdbcTemplate jdbc;

    @Test
    @DisplayName("a client contact invited onto the team sees the invitation in the workspace they are already in")
    void aPureClientSeesTheirStaffInvitation() throws Exception {
        String alok = "alok@" + domain;
        String rana = "rana@client-" + domain;
        String workspaceId = createWorkspace(verifiedUser("Alok Kumar", alok), "Promote Firm");
        String admin = login(alok);
        clientRepresentative(admin, "Rana Client", rana);
        invite(admin, rana, "MEMBER");

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + login(rana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingInvitations.length()").value(1))
                .andExpect(jsonPath("$.pendingInvitations[0].workspaceId").value(workspaceId))
                .andExpect(jsonPath("$.pendingInvitations[0].role").value("MEMBER"));
    }

    @Test
    @DisplayName("staff named a client contact see that invitation; one that would add nothing stays hidden")
    void staffSeeAClientInvitationButNotARepeatOfTheirOwnRole() throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Contact Firm");
        String admin = login(alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");
        String clientId = newClient(admin, "Contact Holdings");
        addContact(admin, clientId, "Sara Al-Mansour", sara);

        // Staff already: the contact door adds the role straight to the membership, so nothing is waiting.
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + login(sara)))
                .andExpect(jsonPath("$.pendingInvitations.length()").value(0));
        assertThat(rolesOf(admin, sara)).containsExactlyInAnyOrder("CLIENT", "MEMBER");
    }

    @Test
    @DisplayName("an account that never proved its address is told nothing about who invited it")
    void anUnverifiedAccountSeesNoInvitations() throws Exception {
        String alok = "alok@" + domain;
        String ceo = "ceo@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Quiet Firm");
        invite(login(alok), ceo, "ADMIN");

        JsonNode signedUp = signup("Not The Ceo", ceo);
        assertThat(signedUp.get("user").get("pendingInvitations")).isEmpty();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + signedUp.get("accessToken").asText()))
                .andExpect(jsonPath("$.pendingInvitations.length()").value(0));
    }

    @Test
    @DisplayName("an unverified account is refused even holding the link: a forwarded invitation is no mailbox proof")
    void anUnverifiedAccountIsRefusedEvenWithTheLink() throws Exception {
        String alok = "alok@" + domain;
        String omar = "omar@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Link Firm");
        String admin = login(alok);
        // Whoever opened this account need not own the mailbox; they keep its password whatever happens next.
        String unverified = signup("Omar Haddad", omar).get("accessToken").asText();
        email.clear();
        invite(admin, omar, "MEMBER");

        MvcResult withLink = mvc.perform(post("/api/v1/onboarding/invitations/accept")
                        .header("Authorization", "Bearer " + unverified)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}
                                """.formatted(email.latestTokenFor(omar))))
                .andExpect(status().isForbidden())
                .andReturn();
        assertThat(codeOf(withLink)).isEqualTo("EMAIL_NOT_VERIFIED");
        mvc.perform(get("/api/v1/invitations").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("the waiting count leaves out an invitation whose link has lapsed")
    void theCountLeavesOutLapsedInvitations() throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Lapse Firm");
        String admin = login(alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");
        invite(admin, "omar@" + domain, "MEMBER");
        invite(admin, "lina@" + domain, "MEMBER");
        lapse("lina@" + domain);

        mvc.perform(get("/api/v1/invitations/pending-count").header("Authorization", "Bearer " + login(sara)))
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    @DisplayName("a resend names the admin who resent it, in the email and on the Team page")
    void aResendNamesTheResender() throws Exception {
        String alok = "alok@" + domain;
        String lina = "lina@" + domain;
        String omar = "omar@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Resend Firm");
        String admin = login(alok);
        inviteAndAccept(admin, "Lina Saleh", lina, "ADMIN");
        invite(admin, omar, "MEMBER");
        String second = login(lina);

        mvc.perform(post("/api/v1/invitations/" + onlyInvitationId(admin) + "/resend")
                        .header("Authorization", "Bearer " + second))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/invitations").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$[0].invitedByName").value("Lina Saleh"));
        assertThat(email.subjectsFor(omar).getLast()).startsWith("Lina Saleh invited you");
    }

    @Test
    @DisplayName("removing an admin withdraws the invitations they sent: their links stop working")
    void removingAnAdminWithdrawsTheirInvitations() throws Exception {
        String alok = "alok@" + domain;
        String lina = "lina@" + domain;
        String omar = "omar@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Remove Firm");
        String admin = login(alok);
        inviteAndAccept(admin, "Lina Saleh", lina, "ADMIN");
        invite(login(lina), omar, "MEMBER");
        String link = email.latestTokenFor(omar);

        mvc.perform(delete("/api/v1/members/" + memberIdOf(admin, lina)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        assertThat(codeOf(preview(link))).isEqualTo("INVITATION_INVALID");
        mvc.perform(get("/api/v1/invitations").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("an admin stepped down to member takes their staff invitations with the role")
    void demotingAnAdminWithdrawsTheirStaffInvitations() throws Exception {
        String alok = "alok@" + domain;
        String lina = "lina@" + domain;
        String omar = "omar@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Demote Firm");
        String admin = login(alok);
        inviteAndAccept(admin, "Lina Saleh", lina, "ADMIN");
        invite(login(lina), omar, "MEMBER");
        String link = email.latestTokenFor(omar);

        changeRoles(admin, memberIdOf(admin, lina), "[\"MEMBER\"]").andExpect(status().isOk());

        assertThat(codeOf(preview(link))).isEqualTo("INVITATION_INVALID");
    }

    @Test
    @DisplayName("promoting someone who is also a client contact keeps their client role")
    void aRoleChangeKeepsTheClientRole() throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Keep Firm");
        String admin = login(alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");
        addContact(admin, newClient(admin, "Keep Holdings"), "Sara Al-Mansour", sara);
        assertThat(rolesOf(admin, sara)).containsExactlyInAnyOrder("CLIENT", "MEMBER");

        changeRoles(admin, memberIdOf(admin, sara), "[\"ADMIN\"]").andExpect(status().isOk());

        assertThat(rolesOf(admin, sara)).containsExactlyInAnyOrder("CLIENT", "ADMIN");
    }

    @Test
    @DisplayName("a role list holding a null is a 400, not a 500")
    void aNullRoleIsRefused() throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Null Firm");
        String admin = login(alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");

        MvcResult refused = changeRoles(admin, memberIdOf(admin, sara), "[null]")
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(codeOf(refused)).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("a client contact's invitation can be cancelled: the link dies and the record forgets them")
    void aContactInvitationCanBeCancelled() throws Exception {
        String alok = "alok@" + domain;
        String typo = "farah@clinet-" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Cancel Firm");
        String admin = login(alok);
        String clientId = newClient(admin, "Cancel Holdings");
        String contactId = addContact(admin, clientId, "Farah Nasser", typo);
        String link = email.latestTokenFor(typo);

        mvc.perform(delete("/api/v1/clients/" + clientId + "/representatives/" + contactId + "/invitation")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        assertThat(codeOf(preview(link))).isEqualTo("INVITATION_INVALID");
        mvc.perform(get("/api/v1/clients/" + clientId).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.representatives.length()").value(0));
        // A second cancel finds nothing to cancel.
        mvc.perform(delete("/api/v1/clients/" + clientId + "/representatives/" + contactId + "/invitation")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a client contact's invitation can be resent: the old link dies, the new one admits, the lapse clears")
    void aContactInvitationCanBeResent() throws Exception {
        String alok = "alok@" + domain;
        String farah = "farah@client-" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Resend Contact Firm");
        String admin = login(alok);
        String clientId = newClient(admin, "Resend Holdings");
        String contactId = addContact(admin, clientId, "Farah Nasser", farah);
        String firstLink = email.latestTokenFor(farah);
        lapse(farah);
        mvc.perform(get("/api/v1/clients/" + clientId).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.representatives[0].invitationExpired").value(true));

        mvc.perform(post("/api/v1/clients/" + clientId + "/representatives/" + contactId + "/resend")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        String secondLink = email.latestTokenFor(farah);
        assertThat(secondLink).isNotEqualTo(firstLink);
        assertThat(codeOf(preview(firstLink))).isEqualTo("INVITATION_INVALID");
        preview(secondLink);
        mvc.perform(get("/api/v1/clients/" + clientId).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.representatives[0].invitationExpired").value(false))
                .andExpect(jsonPath("$.representatives[0].status").value("INVITED"));
    }

    @Test
    @DisplayName("a client contact whose email the provider refused is marked on the record")
    void aRefusedContactEmailIsMarked() throws Exception {
        String alok = "alok@" + domain;
        String farah = "farah@client-" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Bounce Contact Firm");
        String admin = login(alok);
        String clientId = newClient(admin, "Bounce Holdings");
        email.refuseDeliveryTo(farah);
        addContact(admin, clientId, "Farah Nasser", farah);

        mvc.perform(get("/api/v1/clients/" + clientId).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.representatives[0].emailFailed").value(true));
    }

    @Test
    @DisplayName("a client contact removed from the workspace can be invited back onto the record")
    void aRemovedContactCanBeInvitedBack() throws Exception {
        String alok = "alok@" + domain;
        String rana = "rana@client-" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Return Firm");
        String admin = login(alok);
        String clientId = newClient(admin, "Return Holdings");
        addContact(admin, clientId, "Rana Client", rana);
        mvc.perform(post("/api/v1/onboarding/accept-invitation-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","fullName":"Rana Client","password":"%s"}
                                """.formatted(email.latestTokenFor(rana), PASSWORD)))
                .andExpect(status().isCreated());

        // A pure client is not on the staff roster; their membership row is what an admin removes.
        String ranaMemberId = jdbc.queryForObject("""
                SELECT m.id::text FROM app_lm_workspace_member m JOIN app_lm_user u ON u.id = m.user_id
                WHERE u.email = ?""", String.class, rana);
        mvc.perform(delete("/api/v1/members/" + ranaMemberId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/clients/" + clientId).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.representatives.length()").value(0));

        email.clear();
        addContact(admin, clientId, "Rana Client", rana);
        assertThat(email.subjectsFor(rana)).hasSize(1);
    }

    @Test
    @DisplayName("the 'you now represent' notice goes only once the contact is saved")
    void theRepresentativeNoticeWaitsForTheCommit() throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Notice Firm");
        String admin = login(alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");
        String clientId = newClient(admin, "Notice Holdings");
        email.clear();

        addContact(admin, clientId, "Sara Al-Mansour", sara);

        assertThat(email.subjectsFor(sara)).containsExactly("You now represent Notice Holdings on Uncava");
    }

    @Test
    @DisplayName("an invitation email says 'an admin' and is accepted until the date the preview names")
    void thePreviewNamesTheExpiry() throws Exception {
        String alok = "alok@" + domain;
        String lina = "lina@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Wording Firm");
        invite(login(alok), lina, "ADMIN");

        JsonNode preview = body(preview(email.latestTokenFor(lina)));
        assertThat(preview.get("expiresAt").asText()).isNotBlank();
    }

    private ResultActions changeRoles(String adminToken, String memberId, String roles) throws Exception {
        return mvc.perform(patch("/api/v1/members/" + memberId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":" + roles + "}"));
    }

    private MvcResult preview(String link) throws Exception {
        return mvc.perform(get("/api/v1/onboarding/invitations/preview").param("token", link)).andReturn();
    }

    private void lapse(String inviteeEmail) {
        jdbc.update("UPDATE app_lm_invitation SET expires_at = now() - interval '1 minute' WHERE email = ?",
                inviteeEmail);
    }

    private String newClient(String adminToken, String name) throws Exception {
        return body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"%s"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private String addContact(String adminToken, String clientId, String fullName, String contactEmail)
            throws Exception {
        return body(mvc.perform(post("/api/v1/clients/" + clientId + "/representatives")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"%s","position":"Sponsor","email":"%s"}
                                """.formatted(fullName, contactEmail)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
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
        return body(mvc.perform(get("/api/v1/invitations").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()).get(0).get("id").asText();
    }

    private List<String> rolesOf(String callerToken, String memberEmail) throws Exception {
        JsonNode roster = body(mvc.perform(get("/api/v1/members").header("Authorization", "Bearer " + callerToken))
                .andExpect(status().isOk())
                .andReturn());
        for (JsonNode member : roster) {
            if (member.get("email").asText().equals(memberEmail)) {
                List<String> roles = new ArrayList<>();
                member.get("roles").forEach(role -> roles.add(role.asText()));
                return roles;
            }
        }
        throw new AssertionError(memberEmail + " not on the roster: " + roster);
    }
}
