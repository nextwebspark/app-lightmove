package app.lightmove.api.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * A person may belong to several workspaces (V81). What these pin: a staff member founds a further
 * workspace from inside the app and is its admin; an invitation to a second workspace is accepted,
 * not refused; a member who left and is invited back reactivates their row rather than tripping the
 * one-row-per-workspace constraint; and the last-admin guard is unchanged.
 */
@IntegrationTest
class MultiWorkspaceMembershipIntegrationTest extends FlowTestSupport {

    @Test
    @DisplayName("a staff member creates a second workspace from the app and is its admin")
    void staffMemberCreatesAFurtherWorkspace() throws Exception {
        String alokEmail = "alok@" + domain;
        String firstId = createWorkspace(verifiedUser("Alok Kumar", alokEmail), "First Firm");
        String first = login(alokEmail);

        MvcResult created = mvc.perform(post("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + first)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(workspaceBody("Second Firm")))
                .andExpect(status().isCreated())
                // The response names the new workspace so the SPA can switch into it by id...
                .andExpect(jsonPath("$.workspace.name").value("Second Firm"))
                .andExpect(jsonPath("$.workspace.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.workspaces.length()").value(2))
                .andReturn();
        String secondId = body(created).at("/workspace/id").asText();
        assertThat(secondId).isNotEqualTo(firstId);

        // ...while the token the caller holds still says the first one.
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + first))
                .andExpect(jsonPath("$.workspace.id").value(firstId));

        // Creating is an explicit choice: the next sign-in opens in the new workspace.
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + login(alokEmail)))
                .andExpect(jsonPath("$.workspace.id").value(secondId))
                .andExpect(jsonPath("$.workspace.roles[0]").value("ADMIN"));

        // The roster there is the founder alone, and they can invite — the same door as signup step 4.
        mvc.perform(get("/api/v1/members").header("Authorization", "Bearer " + login(alokEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("a pure client representative cannot found a workspace from their portal seat")
    void pureClientCannotCreateAWorkspace() throws Exception {
        String alokEmail = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alokEmail), "Alok Firm");
        String admin = login(alokEmail);

        String repEmail = "rep@client-" + domain;
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Acme Corp"}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        mvc.perform(post("/api/v1/clients/" + clientId + "/representatives")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Rep Person","position":"Advisor","email":"%s"}
                                """.formatted(repEmail)))
                .andExpect(status().isCreated());
        String rep = body(mvc.perform(post("/api/v1/onboarding/accept-invitation-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","fullName":"Rep Person","password":"%s"}
                                """.formatted(email.latestTokenFor(repEmail), PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn()).get("accessToken").asText();

        mvc.perform(post("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + rep)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(workspaceBody("Rep's Own Firm")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an invitation to a second workspace is accepted, and both memberships stay active")
    void invitationToASecondWorkspaceIsAccepted() throws Exception {
        String alokEmail = "alok@" + domain;
        String saraEmail = "sara@" + domain;
        String alokWorkspace = createWorkspace(verifiedUser("Alok Kumar", alokEmail), "Alok Firm");
        String saraWorkspace = createWorkspace(verifiedUser("Sara Al-Mansour", saraEmail), "Sara Firm");

        invite(login(saraEmail), alokEmail, "MEMBER");

        String alok = login(alokEmail);
        MvcResult me = mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + alok))
                .andExpect(jsonPath("$.workspace.id").value(alokWorkspace))
                .andExpect(jsonPath("$.pendingInvitations[0].workspaceName").value("Sara Firm"))
                .andExpect(jsonPath("$.pendingInvitations[0].inviterName").value("Sara Al-Mansour"))
                .andReturn();
        String invitationId = body(me).at("/pendingInvitations/0/id").asText();

        mvc.perform(post("/api/v1/onboarding/invitations/" + invitationId + "/accept")
                        .header("Authorization", "Bearer " + alok))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspace.id").value(saraWorkspace))
                .andExpect(jsonPath("$.workspace.roles[0]").value("MEMBER"))
                .andExpect(jsonPath("$.workspaces.length()").value(2))
                .andExpect(jsonPath("$.pendingInvitations").isEmpty());

        // Joining is an explicit choice: the next sign-in opens there — as a MEMBER, not as the
        // admin he is at home.
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + login(alokEmail)))
                .andExpect(jsonPath("$.workspace.id").value(saraWorkspace))
                .andExpect(jsonPath("$.workspace.roles[0]").value("MEMBER"));
    }

    @Test
    @DisplayName("a member who left and is invited back rejoins on the same row")
    void removedMemberRejoinsOnInvitation() throws Exception {
        String alokEmail = "alok@" + domain;
        String saraEmail = "sara@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alokEmail), "Alok Firm");
        String admin = login(alokEmail);
        inviteAndAccept(admin, "Sara Al-Mansour", saraEmail, "MEMBER");

        mvc.perform(delete("/api/v1/members/" + memberIdOf(admin, saraEmail))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        // Re-invited — as an ADMIN this time. (workspace_id, user_id) is unique whatever the status,
        // so this must reactivate her row rather than insert a second.
        mvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"email":"%s","role":"ADMIN"}]
                                """.formatted(saraEmail)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/onboarding/invitations/accept")
                        .header("Authorization", "Bearer " + login(saraEmail))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}
                                """.formatted(email.latestTokenFor(saraEmail))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspace.name").value("Alok Firm"))
                .andExpect(jsonPath("$.workspace.roles[0]").value("ADMIN"));

        // One row for her on the roster, with the new tenure's role.
        JsonNode roster = body(mvc.perform(get("/api/v1/members").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
        long saraRows = 0;
        for (JsonNode member : roster) {
            if (member.get("email").asText().equals(saraEmail)) {
                saraRows++;
                assertThat(member.get("roles").get(0).asText()).isEqualTo("ADMIN");
            }
        }
        assertThat(saraRows).isEqualTo(1);
    }

    @Test
    @DisplayName("an invitation to a workspace the user is already in is hidden and moot")
    void invitationToAWorkspaceAlreadyJoinedIsMoot() throws Exception {
        String alokEmail = "alok@" + domain;
        String saraEmail = "sara@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alokEmail), "Alok Firm");
        String admin = login(alokEmail);
        inviteAndAccept(admin, "Sara Al-Mansour", saraEmail, "MEMBER");

        // A second invitation to the same address is skipped by the invite itself ("already a
        // member"), so none is pending; /me lists nothing to accept.
        invite(admin, saraEmail, "MEMBER");
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + login(saraEmail)))
                .andExpect(jsonPath("$.pendingInvitations").isEmpty())
                .andExpect(jsonPath("$.workspaces.length()").value(1));
    }

    @Test
    @DisplayName("leaving the workspace a session is in lands the next sign-in in another, or nowhere")
    void leavingFallsThroughToAnotherWorkspaceOrNone() throws Exception {
        String alokEmail = "alok@" + domain;
        String saraEmail = "sara@" + domain;
        String alokWorkspace = createWorkspace(verifiedUser("Alok Kumar", alokEmail), "Alok Firm");
        createWorkspace(verifiedUser("Sara Al-Mansour", saraEmail), "Sara Firm");
        // As a second ADMIN there: leaving is MEMBER_MANAGE, an admin's action, and Sara stays behind.
        String saraAdmin = login(saraEmail);
        invite(saraAdmin, alokEmail, "ADMIN");
        mvc.perform(post("/api/v1/onboarding/invitations/accept")
                        .header("Authorization", "Bearer " + login(alokEmail))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}
                                """.formatted(email.latestTokenFor(alokEmail))))
                .andExpect(status().isOk());

        // Alok's last choice was joining Sara's firm; leaving it falls back to his own.
        String alokInSara = login(alokEmail);
        mvc.perform(delete("/api/v1/members/" + memberIdOf(alokInSara, alokEmail))
                        .header("Authorization", "Bearer " + alokInSara))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + login(alokEmail)))
                .andExpect(jsonPath("$.workspace.id").value(alokWorkspace))
                .andExpect(jsonPath("$.workspaces.length()").value(1));

        // The sole admin of his own firm cannot leave it — the guard is per workspace and unchanged.
        String alokAtHome = login(alokEmail);
        MvcResult leave = mvc.perform(delete("/api/v1/members/" + memberIdOf(alokAtHome, alokEmail))
                        .header("Authorization", "Bearer " + alokAtHome))
                .andReturn();
        assertThat(codeOf(leave)).isEqualTo("LAST_ADMIN");

        // Deleting it leaves him with no workspace at all: back to the wizard.
        mvc.perform(delete("/api/v1/workspace")
                        .header("Authorization", "Bearer " + alokAtHome)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"confirmName":"Alok Firm"}"""))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + login(alokEmail)))
                .andExpect(jsonPath("$.workspace").doesNotExist())
                .andExpect(jsonPath("$.workspaces").isEmpty());
    }

    @Test
    @DisplayName("the signup wizard's organisation step no longer refuses a user who already has a workspace")
    void wizardStepCreatesAFurtherWorkspaceToo() throws Exception {
        String alokEmail = "alok@" + domain;
        String firstId = createWorkspace(verifiedUser("Alok Kumar", alokEmail), "First Firm");
        String secondId = createWorkspace(login(alokEmail), "Second Firm");

        assertThat(secondId).isNotEqualTo(firstId);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + login(alokEmail)))
                .andExpect(jsonPath("$.workspaces.length()").value(2));
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

    private static String workspaceBody(String name) {
        return """
                {"name":"%s","companySize":"11-50 people","primaryRegion":"GCC",
                 "teamFocus":"Executive search"}
                """.formatted(name);
    }
}
