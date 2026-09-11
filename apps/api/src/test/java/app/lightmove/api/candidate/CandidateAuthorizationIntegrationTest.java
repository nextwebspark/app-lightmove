package app.lightmove.api.candidate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
class CandidateAuthorizationIntegrationTest extends FlowTestSupport {

    @Test
    @DisplayName("reading follows the seat: none is refused, either role reads, the workspace admin needs none")
    void readGateFollowsTheSeat() throws Exception {
        Fixture f = fixture("Candidate Read Gate Firm");

        mvc.perform(get(candidatesUrl(f.projectId)).header("Authorization", "Bearer " + f.admin))
                .andExpect(status().isOk());
        mvc.perform(get(candidatesUrl(f.projectId)).header("Authorization", "Bearer " + login(f.saraEmail)))
                .andExpect(status().isForbidden());

        seat(f.admin, f.projectId, f.saraId, "RESEARCHER");
        mvc.perform(get(candidatesUrl(f.projectId)).header("Authorization", "Bearer " + login(f.saraEmail)))
                .andExpect(status().isOk());

        seat(f.admin, f.projectId, f.saraId, "LEAD");
        mvc.perform(get(candidatesUrl(f.projectId)).header("Authorization", "Bearer " + login(f.saraEmail)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("mapping an executive is the seat's work: refused unseated, allowed to a researcher")
    void writeGateFollowsTheSeat() throws Exception {
        Fixture f = fixture("Candidate Write Gate Firm");
        String executive = """
                {"fullName":"Yasmin El-Sayed"}""";

        mvc.perform(post(candidatesUrl(f.projectId))
                        .header("Authorization", "Bearer " + login(f.saraEmail))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(executive))
                .andExpect(status().isForbidden());

        // WORK_EXECUTE, not PROJECT_EDIT: mapping people is the daily work of the seat, not an edit to
        // the mandate's own definition, so a researcher does it.
        seat(f.admin, f.projectId, f.saraId, "RESEARCHER");
        mvc.perform(post(candidatesUrl(f.projectId))
                        .header("Authorization", "Bearer " + login(f.saraEmail))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(executive))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("the status pill is a write: a seated researcher may flick it, an unseated member may not")
    void statusChangeIsAWrite() throws Exception {
        Fixture f = fixture("Candidate Status Matrix Firm");

        // Unseated first, before the seat exists: the pill is the one control on an otherwise
        // read-only panel, so it is exactly the one that must not reach a viewer.
        mvc.perform(patch(candidatesUrl(f.projectId) + "/" + java.util.UUID.randomUUID())
                        .header("Authorization", "Bearer " + login(f.saraEmail))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"contacted"}"""))
                .andExpect(status().isForbidden());

        seat(f.admin, f.projectId, f.saraId, "RESEARCHER");
        String candidateId = body(mvc.perform(post(candidatesUrl(f.projectId))
                        .header("Authorization", "Bearer " + f.admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Omar Haddad"}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();

        mvc.perform(patch(candidatesUrl(f.projectId) + "/" + candidateId)
                        .header("Authorization", "Bearer " + login(f.saraEmail))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"contacted"}"""))
                .andExpect(status().isOk());
    }

    // fixture

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
