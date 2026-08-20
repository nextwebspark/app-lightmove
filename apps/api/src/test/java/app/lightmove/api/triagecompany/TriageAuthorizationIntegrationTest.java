package app.lightmove.api.triagecompany;

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
 * The triage resource's action matrix, both halves.
 *
 * <p>Reading the triaged list is WORK_VIEW — the read half every seated role holds, a CLIENT
 * representative included, so they can follow a mandate. <b>Changing a triage state is
 * WORK_EXECUTE</b>, and the gap between the two is the point: a client representative must be able to
 * see that a company was shortlisted without being able to shortlist one, or to move a company their
 * own firm dislikes to Declined.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class TriageAuthorizationIntegrationTest extends FlowTestSupport {

    @Test
    @DisplayName("an unseated member cannot read a mandate's triaged companies")
    void unseatedMemberCannotRead() throws Exception {
        Fixture f = fixture("Triage Unseated Firm");
        String sara = login(f.saraEmail);

        mvc.perform(get(triageUrl(f.projectId)).header("Authorization", "Bearer " + sara))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a seated researcher can read a mandate's triaged companies")
    void seatedResearcherCanRead() throws Exception {
        Fixture f = fixture("Triage Researcher Firm");
        seat(f.admin, f.projectId, f.saraId, "RESEARCHER");
        String sara = login(f.saraEmail);

        mvc.perform(get(triageUrl(f.projectId)).header("Authorization", "Bearer " + sara))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a seated lead can read a mandate's triaged companies")
    void seatedLeadCanRead() throws Exception {
        Fixture f = fixture("Triage Lead Firm");
        seat(f.admin, f.projectId, f.saraId, "LEAD");
        String sara = login(f.saraEmail);

        mvc.perform(get(triageUrl(f.projectId)).header("Authorization", "Bearer " + sara))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("the workspace admin reads every project's triaged companies without a seat")
    void workspaceAdminBypasses() throws Exception {
        Fixture f = fixture("Triage Workspace Admin Firm");

        mvc.perform(get(triageUrl(f.projectId)).header("Authorization", "Bearer " + f.admin))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a seated researcher may add a company to the universe")
    void seatedResearcherCanAdd() throws Exception {
        Fixture f = fixture("Universe Researcher Write Firm");
        seat(f.admin, f.projectId, f.saraId, "RESEARCHER");

        // WORK_EXECUTE, not PROJECT_EDIT: triage is the daily work of the seat, not an edit to the
        // mandate's own definition, so a researcher does it.
        mvc.perform(post(triageUrl(f.projectId))
                        .header("Authorization", "Bearer " + login(f.saraEmail))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"apolloAccountId":"not-in-the-universe"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("an unseated member cannot add to the universe or bulk-add")
    void unseatedMemberCannotWrite() throws Exception {
        Fixture f = fixture("Universe Unseated Write Firm");
        String sara = login(f.saraEmail);

        mvc.perform(post(triageUrl(f.projectId))
                        .header("Authorization", "Bearer " + sara)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"apolloAccountId":"a1"}"""))
                .andExpect(status().isForbidden());
        mvc.perform(post(triageUrl(f.projectId) + "/from-filter")
                        .header("Authorization", "Bearer " + sara))
                .andExpect(status().isForbidden());
    }

    // ── fixture ──────────────────────────────────────────────────────────────

    private static String triageUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/triage";
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

    private void seat(String leadToken, String projectId, String memberId, String role)
            throws Exception {
        mvc.perform(put("/api/v1/projects/" + projectId + "/members/" + memberId)
                        .header("Authorization", "Bearer " + leadToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"%s"}""".formatted(role)))
                .andExpect(status().isOk());
    }
}
