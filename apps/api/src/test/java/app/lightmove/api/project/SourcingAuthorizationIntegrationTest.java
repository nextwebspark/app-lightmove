package app.lightmove.api.project;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/**
 * Sourcing's action matrix: it shares Strategy's read gate (WORK_EXECUTE), so an unseated member is
 * shut out and every seated project role — RESEARCHER, LEAD, ADMIN — gets in. There is no write side.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class SourcingAuthorizationIntegrationTest extends FlowTestSupport {

    @Test
    @DisplayName("an unseated member cannot read sourcing results")
    void unseatedMemberCannotRead() throws Exception {
        Fixture f = fixture("Sourcing Unseated Firm");
        String sara = login(f.saraEmail);

        mvc.perform(get(sourcingUrl(f.projectId)).header("Authorization", "Bearer " + sara))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a seated researcher can read sourcing results")
    void seatedResearcherCanRead() throws Exception {
        Fixture f = fixture("Sourcing Researcher Firm");
        seat(f.admin, f.projectId, f.saraId, "[\"RESEARCHER\"]");
        String sara = login(f.saraEmail);

        mvc.perform(get(sourcingUrl(f.projectId)).header("Authorization", "Bearer " + sara))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a seated lead can read sourcing results")
    void seatedLeadCanRead() throws Exception {
        Fixture f = fixture("Sourcing Lead Firm");
        seat(f.admin, f.projectId, f.saraId, "[\"LEAD\"]");
        String sara = login(f.saraEmail);

        mvc.perform(get(sourcingUrl(f.projectId)).header("Authorization", "Bearer " + sara))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a seated project admin can read sourcing results")
    void seatedAdminCanRead() throws Exception {
        Fixture f = fixture("Sourcing Admin Firm");
        seat(f.admin, f.projectId, f.saraId, "[\"ADMIN\"]");
        String sara = login(f.saraEmail);

        mvc.perform(get(sourcingUrl(f.projectId)).header("Authorization", "Bearer " + sara))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("the workspace admin reads every project's sourcing results without a seat")
    void workspaceAdminBypasses() throws Exception {
        Fixture f = fixture("Sourcing Workspace Admin Firm");

        mvc.perform(get(sourcingUrl(f.projectId)).header("Authorization", "Bearer " + f.admin))
                .andExpect(status().isOk());
    }

    // ── fixture ──────────────────────────────────────────────────────────────

    private static String sourcingUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/sourcing";
    }

    private record Fixture(String admin, String projectId, String saraEmail, String saraId) {}

    private Fixture fixture(String firmName) throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), firmName);
        String admin = login(alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");

        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Matrix Client"}"""))
                .andReturn()).get("id").asText();
        String projectId = body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"Head of Retail"}
                                """.formatted(clientId)))
                .andReturn()).get("id").asText();

        return new Fixture(admin, projectId, sara, memberIdOf(admin, sara));
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
