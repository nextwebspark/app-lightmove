package app.lightmove.api.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The action matrix, exercised over HTTP through {@code @PreAuthorize}: who may edit a mandate, who
 * may reshape its team, and what a denial looks like on the wire.
 *
 * <p>The last test is the regression net for the guard-bean design itself: an {@code ApiException}
 * thrown inside a SpEL-invoked bean must surface as its own RFC 9457 problem (with the 404 masking
 * for non-members intact), not be swallowed into a generic 403.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class ProjectAuthorizationIntegrationTest extends FlowTestSupport {

    @Test
    @DisplayName("a researcher works the mandate but cannot edit it or reshape the team")
    void researcherIsExecutionOnly() throws Exception {
        Fixture f = fixture("Researcher Matrix Firm");
        seat(f.admin, f.projectId, f.saraId, "[\"RESEARCHER\"]");
        String sara = login(f.saraEmail);

        mvc.perform(patch("/api/v1/projects/" + f.projectId)
                        .header("Authorization", "Bearer " + sara)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetDate":"2027-06-01"}"""))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/v1/projects/" + f.projectId + "/members/" + f.omarId)
                        .header("Authorization", "Bearer " + sara)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles":["RESEARCHER"]}"""))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/v1/projects/" + f.projectId + "/members/" + f.saraId)
                        .header("Authorization", "Bearer " + sara))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a lead edits the mandate but the team is still not theirs to reshape")
    void leadEditsButDoesNotStaff() throws Exception {
        Fixture f = fixture("Lead Matrix Firm");
        seat(f.admin, f.projectId, f.saraId, "[\"LEAD\"]");
        String sara = login(f.saraEmail);

        mvc.perform(patch("/api/v1/projects/" + f.projectId)
                        .header("Authorization", "Bearer " + sara)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetDate":"2027-06-01"}"""))
                .andExpect(status().isOk());

        mvc.perform(put("/api/v1/projects/" + f.projectId + "/members/" + f.omarId)
                        .header("Authorization", "Bearer " + sara)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles":["RESEARCHER"]}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a project admin does everything on their mandate")
    void projectAdminDoesEverything() throws Exception {
        Fixture f = fixture("Project Admin Matrix Firm");
        seat(f.admin, f.projectId, f.saraId, "[\"ADMIN\"]");
        String sara = login(f.saraEmail);

        mvc.perform(patch("/api/v1/projects/" + f.projectId)
                        .header("Authorization", "Bearer " + sara)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetDate":"2027-06-01"}"""))
                .andExpect(status().isOk());

        mvc.perform(put("/api/v1/projects/" + f.projectId + "/members/" + f.omarId)
                        .header("Authorization", "Bearer " + sara)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles":["LEAD","RESEARCHER"]}"""))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("an unseated member reads the list but touches nothing")
    void unseatedMemberIsReadOnly() throws Exception {
        Fixture f = fixture("Unseated Matrix Firm");
        String sara = login(f.saraEmail);

        mvc.perform(patch("/api/v1/projects/" + f.projectId)
                        .header("Authorization", "Bearer " + sara)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetDate":"2027-06-01"}"""))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/v1/projects/" + f.projectId + "/members/" + f.saraId)
                        .header("Authorization", "Bearer " + sara)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles":["RESEARCHER"]}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a workspace admin needs no seat — implicit project admin everywhere")
    void workspaceAdminBypass() throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Bypass Firm");
        String admin = login(alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");
        String saraToken = login(sara);

        // Sara creates the mandate; the workspace admin never gets a seat on it.
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + saraToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Bypass Client"}"""))
                .andReturn()).get("id").asText();
        String projectId = body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + saraToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"CIO"}
                                """.formatted(clientId)))
                .andReturn()).get("id").asText();

        mvc.perform(patch("/api/v1/projects/" + projectId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetDate":"2027-06-01"}"""))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a denial through @PreAuthorize is still an RFC 9457 problem with its own code")
    void denialKeepsItsProblemShape() throws Exception {
        Fixture f = fixture("Problem Shape Firm");
        seat(f.admin, f.projectId, f.saraId, "[\"RESEARCHER\"]");

        MvcResult denied = mvc.perform(patch("/api/v1/projects/" + f.projectId)
                        .header("Authorization", "Bearer " + login(f.saraEmail))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetDate":"2027-06-01"}"""))
                .andReturn();
        assertThat(denied.getResponse().getStatus()).isEqualTo(403);
        assertThat(codeOf(denied)).isEqualTo("FORBIDDEN");

        // And the 404 masking survives the annotation path: no workspace → "not found", not "no".
        String outsider = verifiedUser("Out Sider", "out@other-" + domain);
        MvcResult masked = mvc.perform(patch("/api/v1/projects/" + f.projectId)
                        .header("Authorization", "Bearer " + outsider)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetDate":"2027-06-01"}"""))
                .andReturn();
        assertThat(masked.getResponse().getStatus()).isEqualTo(404);
        assertThat(codeOf(masked)).isEqualTo("NOT_A_MEMBER");
    }

    // ── fixture ──────────────────────────────────────────────────────────────

    private record Fixture(String admin, String projectId, String saraEmail, String saraId,
                           String omarId) {}

    /** A workspace admin, a project the admin created, and two plain members (Sara, Omar). */
    private Fixture fixture(String firmName) throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        String omar = "omar@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), firmName);
        String admin = login(alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");
        inviteAndAccept(admin, "Omar Khalil", omar, "MEMBER");

        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Matrix Client"}"""))
                .andReturn()).get("id").asText();
        String projectId = body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"CFO"}
                                """.formatted(clientId)))
                .andReturn()).get("id").asText();

        return new Fixture(admin, projectId, sara, memberIdOf(admin, sara), memberIdOf(admin, omar));
    }

    private void seat(String adminToken, String projectId, String memberId, String rolesJson)
            throws Exception {
        mvc.perform(put("/api/v1/projects/" + projectId + "/members/" + memberId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles":%s}""".formatted(rolesJson)))
                .andExpect(status().isOk());
    }
}
