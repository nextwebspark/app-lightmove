package app.lightmove.api.position;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import java.util.ArrayList;
import java.util.List;
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
    @DisplayName("the GM-IT fixture proposes a title, location, department and 5 responsibilities, "
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

        // The one line the reader finds arrives already split into the two halves the brief stores,
        // each proposed on its own so each fills, marks and undoes on its own.
        JsonNode city = fieldNamed(fields, "locationCity");
        assertThat(city).isNotNull();
        assertThat(city.get("value").asText()).contains("Dubai");
        assertThat(fieldNamed(fields, "location")).isNull();

        // The cap is exactly 5 — the fixture's heuristic reading finds more than that, and
        // truncateToCeilings cuts it down rather than the heuristic itself limiting the count.
        long responsibilityCount = countFields(fields, "responsibility");
        assertThat(responsibilityCount).isEqualTo(5);
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
        assertThat(brief.get("details").get("locationCountry").asText()).isEqualTo("United Arab Emirates");
        assertThat(brief.get("details").get("locationCity").isNull()).isTrue();

        // "General Manager" matches no seeded template keyword, so the suggestion stays silent
        // rather than offering the generic-executive fallback as though it were a real match.
        assertThat(response.get("suggestedTemplate").isNull()).isTrue();
    }

    @Test
    @DisplayName("a document whose title header matches a template keyword suggests that template")
    void suggestsTheMatchingTemplateFromTheExtractedTitle() throws Exception {
        // None of the four real sample documents heuristically yield a title that also matches a
        // seeded keyword: the CFO brochure is narrative rather than a key-value header (only a live
        // model call reads a title out of it, and this suite runs on StubChatModel — see the class
        // doc), and the GM-IT/JD_CEO fixtures' own titles ("General Manager", "CEO" without the
        // clean header shape HeuristicBriefReader's rule needs) match nothing or nothing reliably.
        // A minimal plain-text fixture with an explicit "Job Title:" header proves the new wiring —
        // suggestedTemplateFor's use of the proposed roleTitle — end to end with no model call.
        String admin = adminOf("Suggestion Firm");
        String projectId = createProject(admin, createClient(admin, "Meridian Holdings", "UAE"), "CFO");
        attach(admin, projectId, new MockMultipartFile("file", "brief.txt", "text/plain",
                        ("Job Title: Chief Financial Officer\n"
                                + "Department: Finance\n"
                                + "Location: Dubai, UAE\n").getBytes()))
                .andExpect(status().isOk());

        JsonNode response = body(mvc.perform(post(extractUrl(projectId))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());

        JsonNode roleTitle = fieldNamed(response.get("fields"), "roleTitle");
        assertThat(roleTitle).isNotNull();
        assertThat(roleTitle.get("value").asText()).isEqualTo("Chief Financial Officer");

        JsonNode suggested = response.get("suggestedTemplate");
        assertThat(suggested.isNull()).isFalse();
        assertThat(suggested.get("code").asText()).isEqualTo("chief-financial-officer");
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
                                {"roleTitle":"%s","department":null,"locationCity":null,
                                 "locationCountry":null,"employmentType":null,"seniority":null,
                                 "responsibilities":[],"narrative":null}""".formatted(proposedTitle)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.details.roleTitle").value(proposedTitle));

        mvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$[0].positionTitle").value(proposedTitle));
    }

    @Test
    @DisplayName("nothing to read without a document")
    void refusesWithoutADocument() throws Exception {
        String admin = adminOf("No Document Firm");
        String projectId = createProject(admin, createClient(admin, "Aldar", "UAE"), "CFO");

        mvc.perform(post(extractUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
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
                .andExpect(jsonPath("$.code").value("POSITION_DOCUMENT_UNREADABLE"));
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

    @Test
    @DisplayName("step two, step three and step five extract routes exist, are gated the same way, "
            + "and write nothing")
    void extractsContextAndCompensationWithoutWriting() throws Exception {
        String admin = adminOf("Context Compensation Extraction Firm");
        String clientId = createClient(admin, "Meridian Holdings", "UAE");
        String projectId = createProject(admin, clientId, "CFO");
        attach(admin, projectId, gmItFixture()).andExpect(status().isOk());
        String beforeUpdatedAt = updatedAtOf(projectId);

        mvc.perform(post(positionUrl(projectId) + "/document/extract/context")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extractionSource").exists());
        mvc.perform(post(positionUrl(projectId) + "/document/extract/assessment")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extractionSource").exists());
        mvc.perform(post(positionUrl(projectId) + "/document/extract/reporting")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extractionSource").exists());

        assertThat(updatedAtOf(projectId)).isEqualTo(beforeUpdatedAt);
    }

    @Test
    @DisplayName("the compensation route is gone, document attached or not")
    void compensationRouteIsGone() throws Exception {
        String admin = adminOf("Compensation Retired Firm");
        String projectId = createProject(admin, createClient(admin, "Aldar", "UAE"), "CFO");

        mvc.perform(post(positionUrl(projectId) + "/document/extract/compensation")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());

        attach(admin, projectId, gmItFixture()).andExpect(status().isOk());
        mvc.perform(post(positionUrl(projectId) + "/document/extract/compensation")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("nothing to read on step two, step three or step five without a document")
    void refusesContextAndCompensationWithoutADocument() throws Exception {
        String admin = adminOf("No Document Context Compensation Firm");
        String projectId = createProject(admin, createClient(admin, "Aldar", "UAE"), "CFO");

        mvc.perform(post(positionUrl(projectId) + "/document/extract/context")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
        mvc.perform(post(positionUrl(projectId) + "/document/extract/assessment")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
        mvc.perform(post(positionUrl(projectId) + "/document/extract/reporting")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PROJECT_EDIT is required for step two, step three and step five too")
    void researcherCannotExtractContextOrCompensation() throws Exception {
        String admin = adminOf("Researcher Context Compensation Firm");
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

        mvc.perform(post(positionUrl(projectId) + "/document/extract/context")
                        .header("Authorization", "Bearer " + login(sara)))
                .andExpect(status().isForbidden());
        mvc.perform(post(positionUrl(projectId) + "/document/extract/assessment")
                        .header("Authorization", "Bearer " + login(sara)))
                .andExpect(status().isForbidden());
        mvc.perform(post(positionUrl(projectId) + "/document/extract/reporting")
                        .header("Authorization", "Bearer " + login(sara)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("another workspace's project is not found for step two, step three or step five either")
    void refusesContextAndCompensationOutsideTheCallersWorkspace() throws Exception {
        String owner = adminOf("Extraction Tenant Context Compensation Firm");
        String projectId = createProject(owner, createClient(owner, "Aldar", "UAE"), "CFO");

        createWorkspace(verifiedUser("Nadia Rahman", "nadia@other-" + domain), "Other Firm");
        String outsider = login("nadia@other-" + domain);

        mvc.perform(post(positionUrl(projectId) + "/document/extract/context")
                        .header("Authorization", "Bearer " + outsider))
                .andExpect(status().isNotFound());
        mvc.perform(post(positionUrl(projectId) + "/document/extract/assessment")
                        .header("Authorization", "Bearer " + outsider))
                .andExpect(status().isNotFound());
        mvc.perform(post(positionUrl(projectId) + "/document/extract/reporting")
                        .header("Authorization", "Bearer " + outsider))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a title matching a template proposes nothing beyond what the document itself states")
    void detailsNeverProposesFromTheTemplate() throws Exception {
        String admin = adminOf("No Backfill Firm");
        String projectId = createProject(admin, createClient(admin, "Meridian Holdings", "UAE"), "CFO");
        attach(admin, projectId, new MockMultipartFile("file", "brief.txt", "text/plain",
                        "Job Title: Chief Financial Officer\n".getBytes()))
                .andExpect(status().isOk());

        JsonNode response = body(mvc.perform(post(extractUrl(projectId))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());

        // "Chief Financial Officer" matches a seeded template that would once have backfilled
        // department, employmentType, narrative and responsibilities — none of that is proposed now.
        JsonNode fields = response.get("fields");
        assertThat(fieldNamed(fields, "roleTitle")).isNotNull();
        assertThat(fieldNamed(fields, "department")).isNull();
        assertThat(fieldNamed(fields, "employmentType")).isNull();
        assertThat(fieldNamed(fields, "narrative")).isNull();
        assertThat(fieldNamed(fields, "responsibility")).isNull();
        assertThat(response.get("suggestedTemplate").get("code").asText()).isEqualTo("chief-financial-officer");
    }

    @Test
    @DisplayName("the reporting reading carries the matched template's usual direct reports")
    void reportingCarriesTheMatchedTemplatesUsualDirectReports() throws Exception {
        String admin = adminOf("Usual Direct Reports Firm");
        String projectId = createProject(admin, createClient(admin, "Aldar", "UAE"), "Chief Financial Officer");
        attach(admin, projectId, gmItFixture()).andExpect(status().isOk());

        JsonNode response = body(mvc.perform(post(positionUrl(projectId) + "/document/extract/reporting")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());

        JsonNode usualDirectReports = response.get("usualDirectReports");
        assertThat(usualDirectReports.isNull()).isFalse();
        List<String> reports = new ArrayList<>();
        usualDirectReports.forEach(node -> reports.add(node.asText()));
        assertThat(reports).containsExactly(
                "Financial Controller", "Head of Treasury", "Head of FP&A", "Head of Investor Relations");
    }

    @Test
    @DisplayName("a title matching no template carries null usualDirectReports")
    void reportingCarriesNoUsualDirectReportsForAnUnmatchedTitle() throws Exception {
        String admin = adminOf("No Usual Direct Reports Firm");
        String projectId = createProject(admin, createClient(admin, "Aldar", "UAE"),
                "Underwater Basket Weaving Specialist");
        attach(admin, projectId, gmItFixture()).andExpect(status().isOk());

        JsonNode response = body(mvc.perform(post(positionUrl(projectId) + "/document/extract/reporting")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(response.get("usualDirectReports").isNull()).isTrue();
    }

    // AC4 ("an accepted criterion survives a later template re-apply") is proved generically by
    // PositionTemplateIntegrationTest#applyingATemplateKeepsWhatSomebodyTyped: it PUTs source:MANUAL
    // and source:DOCUMENT criteria — the shapes a typed or an accepted assessment proposal are written
    // as — then applies a different template, and asserts both survive while the template-drafted one
    // does not. Not duplicated here since the mechanism is the write path, not the extraction call.

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
