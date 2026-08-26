package app.lightmove.api.candidate;

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
 * The candidates resource's action matrix, both halves.
 *
 * <p>Reading a mandate's mapped executives is WORK_VIEW — the read half every seated role holds, a
 * CLIENT representative included, so the hiring company can follow the mapping. <b>Every write is
 * WORK_EXECUTE</b>, and the gap is the point: a client representative must be able to see who has been
 * identified without being able to add a name, rewrite someone's compensation, or delete an executive
 * they would rather not see on the list.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class CandidateAuthorizationIntegrationTest extends FlowTestSupport {

    @Test
    @DisplayName("an unseated member cannot read a mandate's candidates")
    void unseatedMemberCannotRead() throws Exception {
        Fixture f = fixture("Candidate Unseated Firm");
        String sara = login(f.saraEmail);

        mvc.perform(get(candidatesUrl(f.projectId)).header("Authorization", "Bearer " + sara))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a seated researcher can read a mandate's candidates")
    void seatedResearcherCanRead() throws Exception {
        Fixture f = fixture("Candidate Researcher Firm");
        seat(f.admin, f.projectId, f.saraId, "RESEARCHER");

        mvc.perform(get(candidatesUrl(f.projectId))
                        .header("Authorization", "Bearer " + login(f.saraEmail)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a seated lead can read a mandate's candidates")
    void seatedLeadCanRead() throws Exception {
        Fixture f = fixture("Candidate Lead Firm");
        seat(f.admin, f.projectId, f.saraId, "LEAD");

        mvc.perform(get(candidatesUrl(f.projectId))
                        .header("Authorization", "Bearer " + login(f.saraEmail)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("the workspace admin reads every project's candidates without a seat")
    void workspaceAdminBypasses() throws Exception {
        Fixture f = fixture("Candidate Workspace Admin Firm");

        mvc.perform(get(candidatesUrl(f.projectId)).header("Authorization", "Bearer " + f.admin))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a seated researcher may map an executive")
    void seatedResearcherCanWrite() throws Exception {
        Fixture f = fixture("Candidate Researcher Write Firm");
        seat(f.admin, f.projectId, f.saraId, "RESEARCHER");

        // WORK_EXECUTE, not PROJECT_EDIT: mapping people is the daily work of the seat, not an edit to
        // the mandate's own definition, so a researcher does it.
        mvc.perform(post(candidatesUrl(f.projectId))
                        .header("Authorization", "Bearer " + login(f.saraEmail))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Yasmin El-Sayed"}"""))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("an unseated member cannot map an executive")
    void unseatedMemberCannotWrite() throws Exception {
        Fixture f = fixture("Candidate Unseated Write Firm");
        String sara = login(f.saraEmail);

        mvc.perform(post(candidatesUrl(f.projectId))
                        .header("Authorization", "Bearer " + sara)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Yasmin El-Sayed"}"""))
                .andExpect(status().isForbidden());
    }

    // ── fixture ──────────────────────────────────────────────────────────────

    private static String candidatesUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/candidates";
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
