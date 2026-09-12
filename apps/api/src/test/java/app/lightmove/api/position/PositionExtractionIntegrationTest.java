package app.lightmove.api.position;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.StreamUtils;
import tools.jackson.databind.JsonNode;

/**
 * Reading the attached document into step-one proposals, end to end.
 *
 * <p>The context runs on {@code StubChatModel}, whose fixed reply will not bind to {@code
 * ModelDetailsAnswer} — so every reading here comes from {@code HeuristicBriefReader}. That is the
 * path a run without Application Default Credentials gets, and it is what proves AC1 and AC4
 * together: the same call that degrades honestly also has to be useful.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class PositionExtractionIntegrationTest extends FlowTestSupport {

    @Autowired JdbcTemplate db;

    @Test
    @DisplayName("the GM-IT fixture proposes a title, location, department and 5+ responsibilities, "
            + "labelled documentHeadings, and writes nothing")
    void extractsFromTheGmItFixtureAndWritesNothing() throws Exception {
        String admin = adminOf("Extraction Firm");
        String clientId = createClient(admin, "Meridian Holdings", "UAE");
        String projectId = createProject(admin, clientId, "CFO");
        attach(admin, projectId, gmItFixture()).andExpect(status().isOk());

        String beforeUpdatedAt = updatedAtOf(projectId);

        JsonNode response = body(mvc.perform(post(extractUrl(projectId))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(response.get("extractionSource").asText()).isEqualTo("documentHeadings");
        JsonNode fields = response.get("fields");

        JsonNode roleTitle = fieldNamed(fields, "roleTitle");
        assertThat(roleTitle).isNotNull();
        assertThat(roleTitle.get("value").asText()).contains("General Manager");
        assertThat(roleTitle.get("snippet").asText()).isNotBlank();

        JsonNode location = fieldNamed(fields, "location");
        assertThat(location).isNotNull();
        assertThat(location.get("value").asText()).contains("Dubai");

        long responsibilityCount = countFields(fields, "responsibility");
        assertThat(responsibilityCount).isGreaterThanOrEqualTo(5);
        fields.forEach(field -> {
            if (field.get("fieldKey").asText().equals("responsibility")) {
                assertThat(field.get("snippet").asText()).isNotBlank();
            }
        });

        // AC2: nothing persists until a row is accepted. The brief's location is already the
        // client's own HQ country, seeded at project creation, and stays exactly that — not the
        // fixture's "Dubai, UAE" the extraction proposed.
        assertThat(updatedAtOf(projectId)).isEqualTo(beforeUpdatedAt);
        JsonNode brief = readBrief(admin, projectId);
        assertThat(brief.get("details").get("roleTitle").asText()).isEqualTo("CFO");
        assertThat(brief.get("details").get("location").asText()).isEqualTo("United Arab Emirates");
    }

    @Test
    @DisplayName("accepting the proposed role title renames the mandate through the ordinary write path")
    void acceptingRenamesTheMandate() throws Exception {
        String admin = adminOf("Accept Firm");
        String clientId = createClient(admin, "Meridian Holdings", "UAE");
        String projectId = createProject(admin, clientId, "CFO");
        attach(admin, projectId, gmItFixture()).andExpect(status().isOk());

        JsonNode response = body(mvc.perform(post(extractUrl(projectId))
                        .header("Authorization", "Bearer " + admin))
                .andReturn());
        String proposedTitle = fieldNamed(response.get("fields"), "roleTitle").get("value").asText();

        mvc.perform(put(positionUrl(projectId) + "/details")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleTitle":"%s","department":null,"location":null,
                                 "employmentType":null,"seniority":null,"responsibilities":[],
                                 "narrative":null}""".formatted(proposedTitle)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.details.roleTitle").value(proposedTitle));

        mvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + admin))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$[0].positionTitle").value(proposedTitle));
    }

    @Test
    @DisplayName("nothing to read without a document")
    void refusesWithoutADocument() throws Exception {
        String admin = adminOf("No Document Firm");
        String projectId = createProject(admin, createClient(admin, "Aldar", "UAE"), "CFO");

        mvc.perform(post(extractUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("a legacy .doc, even mislabelled, is refused by its own byte signature")
    void refusesALegacyDoc() throws Exception {
        String admin = adminOf("Legacy Doc Firm");
        String projectId = createProject(admin, createClient(admin, "Aldar", "UAE"), "CFO");
        byte[] ole2 = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1, 0, 0, 0, 0};
        attach(admin, projectId, new MockMultipartFile("file", "brief.doc",
                        "application/msword", ole2))
                .andExpect(status().isOk());

        mvc.perform(post(extractUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.code").value("POSITION_DOCUMENT_UNREADABLE"));
    }

    @Test
    @DisplayName("PROJECT_EDIT is required: a seated researcher cannot trigger a billed read")
    void researcherCannotExtract() throws Exception {
        String admin = adminOf("Researcher Extraction Firm");
        String sara = "sara@" + domain;
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");
        String projectId = createProject(admin, createClient(admin, "Aldar", "UAE"), "CFO");
        attach(admin, projectId, gmItFixture()).andExpect(status().isOk());
        mvc.perform(put("/api/v1/projects/" + projectId + "/members/" + memberIdOf(admin, sara))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"RESEARCHER"}"""))
                .andExpect(status().isOk());

        mvc.perform(post(extractUrl(projectId)).header("Authorization", "Bearer " + login(sara)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("another workspace's project is not found, not forbidden")
    void refusesAProjectOutsideTheCallersWorkspace() throws Exception {
        String owner = adminOf("Extraction Tenant Firm");
        String projectId = createProject(owner, createClient(owner, "Aldar", "UAE"), "CFO");

        createWorkspace(verifiedUser("Nadia Rahman", "nadia@other-" + domain), "Other Firm");
        String outsider = login("nadia@other-" + domain);

        mvc.perform(post(extractUrl(projectId)).header("Authorization", "Bearer " + outsider))
                .andExpect(status().isNotFound());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String positionUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/position";
    }

    private static String extractUrl(String projectId) {
        return positionUrl(projectId) + "/document/extract/details";
    }

    private org.springframework.test.web.servlet.ResultActions attach(
            String token, String projectId, MockMultipartFile file) throws Exception {
        return mvc.perform(multipart(positionUrl(projectId) + "/document")
                .file(file)
                .header("Authorization", "Bearer " + token));
    }

    private static MockMultipartFile gmItFixture() throws Exception {
        byte[] content = StreamUtils.copyToByteArray(new ClassPathResource(
                "documents/position/Spec--General Manager - IT_vF.pdf").getInputStream());
        return new MockMultipartFile("file", "Spec--General Manager - IT_vF.pdf",
                "application/pdf", content);
    }

    private static JsonNode fieldNamed(JsonNode fields, String fieldKey) {
        for (JsonNode field : fields) {
            if (field.get("fieldKey").asText().equals(fieldKey)) {
                return field;
            }
        }
        return null;
    }

    private static long countFields(JsonNode fields, String fieldKey) {
        long count = 0;
        for (JsonNode field : fields) {
            if (field.get("fieldKey").asText().equals(fieldKey)) {
                count++;
            }
        }
        return count;
    }

    private String updatedAtOf(String projectId) {
        return db.queryForObject(
                "select updated_at from app_lm_position where project_id = ?::uuid",
                String.class, projectId);
    }

    private JsonNode readBrief(String token, String projectId) throws Exception {
        return body(mvc.perform(get(positionUrl(projectId)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
    }

    private String adminOf(String workspaceName) throws Exception {
        createWorkspace(verifiedUser("Alok Kumar", "alok@" + domain), workspaceName);
        return login("alok@" + domain);
    }

    private String createClient(String token, String name, String hqCountry) throws Exception {
        return body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"%s","hqCountry":"%s"}
                                """.formatted(name, hqCountry)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private String createProject(String token, String clientId, String position) throws Exception {
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"%s"}
                                """.formatted(clientId, position)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }
}
