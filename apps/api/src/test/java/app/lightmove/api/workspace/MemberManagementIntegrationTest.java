package app.lightmove.api.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** The roster: who may see it, change roles on it, and leave it — and the guards that protect it. */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class MemberManagementIntegrationTest extends FlowTestSupport {

    @Test
    @DisplayName("any member sees the roster; an outsider sees no workspace at all")
    void rosterVisibility() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Roster Firm");
        String admin = login(alok);
        inviteAndAccept(admin, "Sara Al-Mansour", "sara@" + domain, "MEMBER");

        mvc.perform(get("/api/v1/members").header("Authorization", "Bearer " + login("sara@" + domain)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // Verified, but in no workspace: 404, because a 403 would confirm the workspace exists.
        String outsider = verifiedUser("Out Sider", "out@other-" + domain);
        mvc.perform(get("/api/v1/members").header("Authorization", "Bearer " + outsider))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("an admin changes a member's roles; a non-admin may not")
    void roleChange() throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Roles Firm");
        String admin = login(alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");
        String saraMemberId = memberIdOf(admin, sara);

        mvc.perform(patch("/api/v1/members/" + saraMemberId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles":["ADMIN","MEMBER"]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(2))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"));

        // Back to plain member, then Sara — no longer an admin — tries to promote herself.
        mvc.perform(patch("/api/v1/members/" + saraMemberId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles":["MEMBER"]}"""))
                .andExpect(status().isOk());

        mvc.perform(patch("/api/v1/members/" + saraMemberId)
                        .header("Authorization", "Bearer " + login(sara))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles":["ADMIN"]}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the CLIENT role cannot be granted through the roster — it belongs to the portal phase")
    void clientRoleIsUnreachable() throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "No Client Firm");
        String admin = login(alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");

        MvcResult refused = mvc.perform(patch("/api/v1/members/" + memberIdOf(admin, sara))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles":["CLIENT"]}"""))
                .andReturn();
        assertThat(refused.getResponse().getStatus()).isEqualTo(400);

        mvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"email":"cfo@client-%s","role":"CLIENT"}]
                                """.formatted(domain)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("the only admin can neither demote themselves nor leave")
    void lastAdminGuard() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Solo Firm");
        String admin = login(alok);
        String selfId = memberIdOf(admin, alok);

        MvcResult demote = mvc.perform(patch("/api/v1/members/" + selfId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles":["MEMBER"]}"""))
                .andReturn();
        assertThat(demote.getResponse().getStatus()).isEqualTo(409);
        assertThat(codeOf(demote)).isEqualTo("LAST_ADMIN");

        MvcResult leave = mvc.perform(delete("/api/v1/members/" + selfId)
                        .header("Authorization", "Bearer " + admin))
                .andReturn();
        assertThat(codeOf(leave)).isEqualTo("LAST_ADMIN");
    }

    @Test
    @DisplayName("with a second admin in place, the first may step down")
    void secondAdminUnlocksDemotion() throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Two Admin Firm");
        String admin = login(alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "ADMIN");

        mvc.perform(patch("/api/v1/members/" + memberIdOf(admin, alok))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles":["MEMBER"]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("MEMBER"));
    }

    @Test
    @DisplayName("a removed member is freed to create a workspace of their own")
    void removalFreesTheMembership() throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Departure Firm");
        String admin = login(alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");

        mvc.perform(delete("/api/v1/members/" + memberIdOf(admin, sara))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        // The partial unique index held one ACTIVE membership; removal released it.
        createWorkspace(login(sara), "Sara's Own Firm");
    }

    @Test
    @DisplayName("the only project admin on a live mandate cannot be removed until it is handed over")
    void soleProjectAdminBlocksRemoval() throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Handover Firm");
        String admin = login(alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");
        String saraMemberId = memberIdOf(admin, sara);
        String saraToken = login(sara);

        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Meridian Energy"}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();

        // Sara creates the mandate, becoming its only project admin.
        String projectId = body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + saraToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"CFO"}
                                """.formatted(clientId)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();

        MvcResult blocked = mvc.perform(delete("/api/v1/members/" + saraMemberId)
                        .header("Authorization", "Bearer " + admin))
                .andReturn();
        assertThat(codeOf(blocked)).isEqualTo("MEMBER_LEADS_PROJECTS");

        // Hand the mandate over — seat the workspace admin as a project admin — and removal goes through.
        mvc.perform(put("/api/v1/projects/" + projectId + "/members/" + memberIdOf(admin, alok))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles":["ADMIN"]}"""))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/v1/members/" + saraMemberId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        // Her seat went with her; the team is just the new admin now.
        mvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].team.length()").value(1))
                .andExpect(jsonPath("$[0].team[0].projectRoles[0]").value("ADMIN"));
    }

    @Test
    @DisplayName("a plain lead — not the only admin — is removable; leads are not structural")
    void leadDoesNotBlockRemoval() throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Plural Leads Firm");
        String admin = login(alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");
        String saraMemberId = memberIdOf(admin, sara);

        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Northwind"}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();

        String projectId = body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"CTO"}
                                """.formatted(clientId)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();

        mvc.perform(put("/api/v1/projects/" + projectId + "/members/" + saraMemberId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles":["LEAD"]}"""))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/v1/members/" + saraMemberId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("an admin of one firm cannot touch another firm's member ids")
    void crossWorkspaceProbeIs404() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Target Firm");
        String targetMemberId = memberIdOf(login(alok), alok);

        String rivalEmail = "boss@rival-" + domain;
        createWorkspace(verifiedUser("Rival Boss", rivalEmail), "Rival Firm");
        String rival = login(rivalEmail);

        mvc.perform(patch("/api/v1/members/" + targetMemberId)
                        .header("Authorization", "Bearer " + rival)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles":["MEMBER"]}"""))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/v1/members/" + targetMemberId)
                        .header("Authorization", "Bearer " + rival))
                .andExpect(status().isNotFound());
    }
}
