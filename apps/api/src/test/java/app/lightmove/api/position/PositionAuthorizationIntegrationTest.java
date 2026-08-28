package app.lightmove.api.position;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The position brief's action matrix: reading needs a seat (WORK_VIEW, held by every project role)
 * and every write needs PROJECT_EDIT on it — a researcher reads a brief but does not define one.
 * Publishing and the attached document are writes like any other, and cross-tenant reads keep the
 * 404 masking.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class PositionAuthorizationIntegrationTest extends FlowTestSupport {

    @Test
    @DisplayName("an unseated member can neither read nor write the brief")
    void unseatedMemberCannotRead() throws Exception {
        Fixture f = fixture("Unseated Position Firm");
        String sara = login(f.saraEmail);

        mvc.perform(get(positionUrl(f.projectId)).header("Authorization", "Bearer " + sara))
                .andExpect(status().isForbidden());

        mvc.perform(put(positionUrl(f.projectId) + "/details")
                        .header("Authorization", "Bearer " + sara)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DETAILS_STEP))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a seated researcher reads the brief but does not define it")
    void researcherReadsButCannotWriteTheBrief() throws Exception {
        Fixture f = fixture("Researcher Position Firm");
        seat(f.admin, f.projectId, f.saraId, "RESEARCHER");
        String sara = login(f.saraEmail);

        mvc.perform(get(positionUrl(f.projectId)).header("Authorization", "Bearer " + sara))
                .andExpect(status().isOk());

        mvc.perform(put(positionUrl(f.projectId) + "/criteria")
                        .header("Authorization", "Bearer " + sara)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"criteria":[{"text":"X","mode":"REQUIRED","fromBrief":false}]}"""))
                .andExpect(status().isForbidden());

        mvc.perform(put(positionUrl(f.projectId) + "/details")
                        .header("Authorization", "Bearer " + sara)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DETAILS_STEP))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("publishing is an ordinary write, so a researcher cannot do it either")
    void researcherCannotPublish() throws Exception {
        Fixture f = fixture("Publish Matrix Firm");
        seat(f.admin, f.projectId, f.saraId, "RESEARCHER");
        String sara = login(f.saraEmail);

        mvc.perform(post(positionUrl(f.projectId) + "/publish")
                        .header("Authorization", "Bearer " + sara))
                .andExpect(status().isForbidden());
        mvc.perform(delete(positionUrl(f.projectId) + "/publish")
                        .header("Authorization", "Bearer " + sara))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a researcher opens the mandate's position description but cannot change it")
    void researcherReadsTheDocumentButCannotAttachOne() throws Exception {
        Fixture f = fixture("Document Matrix Firm");
        seat(f.admin, f.projectId, f.saraId, "RESEARCHER");
        String sara = login(f.saraEmail);

        mvc.perform(multipart(positionUrl(f.projectId) + "/document")
                        .file(new MockMultipartFile("file", "brief.pdf", "application/pdf",
                                "draft".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + sara))
                .andExpect(status().isForbidden());
        mvc.perform(delete(positionUrl(f.projectId) + "/document")
                        .header("Authorization", "Bearer " + sara))
                .andExpect(status().isForbidden());

        // Reading is WORK_VIEW: there is no document yet, so a 404 rather than a 403 is the pass.
        mvc.perform(get(positionUrl(f.projectId) + "/document")
                        .header("Authorization", "Bearer " + sara))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("another workspace's brief does not exist, even to a verified user")
    void crossTenantReadsAreMasked() throws Exception {
        Fixture f = fixture("Masked Position Firm");
        String outsider = verifiedUser("Out Sider", "out@other-" + domain);

        MvcResult masked = mvc.perform(get(positionUrl(f.projectId))
                        .header("Authorization", "Bearer " + outsider))
                .andReturn();
        assertThat(masked.getResponse().getStatus()).isEqualTo(404);
        assertThat(codeOf(masked)).isEqualTo("NOT_A_MEMBER");
    }

    private static final String DETAILS_STEP = """
            {"roleTitle":"CFO","department":null,"location":null,"employmentType":null,
             "seniority":null,"responsibilities":[],"narrative":null}""";

    private static String positionUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/position";
    }

    // ── fixture ──────────────────────────────────────────────────────────────

    private record Fixture(String admin, String projectId, String saraEmail, String saraId) {}

    /** A workspace admin, a project the admin created, and one plain member (Sara). */
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
                                {"clientId":"%s","positionTitle":"CFO"}
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
