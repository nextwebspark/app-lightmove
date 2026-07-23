package app.lightmove.api.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FakeBriefLlmClient;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import app.lightmove.api.core.llm.model.BriefExtraction;
import app.lightmove.api.core.llm.model.BriefExtraction.ExtractedCompetency;
import app.lightmove.api.core.llm.model.BriefExtraction.ExtractedCriterion;
import app.lightmove.api.project.repository.PositionDocumentRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Uploading a position-description document: the fake LLM's extraction lands on the brief, a failed
 * extraction rolls back cleanly, a locked position refuses uploads, and Remove clears the file without
 * undoing what it already populated.
 */
@IntegrationTest
@Import({RecordingEmailSender.Config.class, FakeBriefLlmClient.Config.class})
@TestPropertySource(properties = "lightmove.llm.max-document-size=1KB")
class PositionBriefDocumentFlowIntegrationTest extends FlowTestSupport {

    @Autowired FakeBriefLlmClient llm;
    @Autowired PositionDocumentRepository documentRows;

    @Test
    @DisplayName("a parsed document overwrites what it found and leaves everything else alone")
    void extractionAppliesFoundFieldsAndKeepsTheRest() throws Exception {
        String admin = adminOf("Extraction Firm");
        String projectId = createProject(admin, createClient(admin, "Meridian Energy", "UAE"), "CFO");

        // A real edit already on the brief, on a field the extraction below will NOT mention.
        mvc.perform(put(positionUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mandateReason":"NEW_ROLE","currency":"USD","ltip":"Existing LTIP note",
                                 "confidential":false}"""))
                .andExpect(status().isOk());
        // A criterion the user typed themselves, not from any brief.
        mvc.perform(put(positionUrl(projectId) + "/criteria")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"criteria":[{"text":"Own language requirement","mode":"PREFERRED","fromBrief":false}]}"""))
                .andExpect(status().isOk());

        llm.respondWith(new BriefExtraction(
                "SUCCESSION", "Board wants continuity", "A financially astute, hands-on operator.",
                "Group CFO", 5, 40, "Dubai, UAE", "FIXED_TERM_CONTRACT",
                500000L, 650000L, "AED", 3, "MONTHS", 35, null,
                List.of("Housing allowance", "School fees"),
                List.of(new ExtractedCriterion("IFRS reporting experience", "REQUIRED"),
                        new ExtractedCriterion("Arabic fluency", "PREFERRED")),
                List.of(new ExtractedCompetency("Treasury", 60), new ExtractedCompetency("M&A", 40)),
                List.of(new ExtractedCompetency("Stakeholder Leadership", 100))));

        MockMultipartFile file = new MockMultipartFile("file", "cfo-pd.txt", "text/plain",
                "Chief Financial Officer position description...".getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart(positionUrl(projectId) + "/brief-document")
                        .file(file)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mandateReason").value("SUCCESSION"))
                .andExpect(jsonPath("$.internalContext").value("Board wants continuity"))
                .andExpect(jsonPath("$.reportsTo").value("Group CFO"))
                .andExpect(jsonPath("$.employmentType").value("FIXED_TERM_CONTRACT"))
                .andExpect(jsonPath("$.salaryMax").value(650000))
                .andExpect(jsonPath("$.currency").value("AED"))
                // Not mentioned by the extraction (ltip was null): the earlier real edit survives.
                .andExpect(jsonPath("$.ltip").value("Existing LTIP note"))
                .andExpect(jsonPath("$.criteria.length()").value(3))
                // The kept-user-criteria come first (see PositionService#applyExtraction), then what
                // was just extracted — a criterion the user typed themselves is never touched.
                .andExpect(jsonPath("$.criteria[0].text").value("Own language requirement"))
                .andExpect(jsonPath("$.criteria[0].fromBrief").value(false))
                .andExpect(jsonPath("$.criteria[1].text").value("IFRS reporting experience"))
                .andExpect(jsonPath("$.criteria[1].fromBrief").value(true))
                .andExpect(jsonPath("$.criteria[1].mode").value("REQUIRED"))
                .andExpect(jsonPath("$.criteria[2].text").value("Arabic fluency"))
                .andExpect(jsonPath("$.technical[0].name").value("Treasury"))
                .andExpect(jsonPath("$.technical[0].weight").value(60))
                .andExpect(jsonPath("$.behavioural[0].name").value("Stakeholder Leadership"))
                .andExpect(jsonPath("$.briefDocument.fileName").value("cfo-pd.txt"))
                .andExpect(jsonPath("$.briefDocument.contentType").value("text/plain"))
                .andExpect(jsonPath("$.briefDocument.status").value("COMPLETED"));

        mvc.perform(get(positionUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.briefDocument.fileName").value("cfo-pd.txt"));
    }

    @Test
    @DisplayName("a failed extraction leaves the brief and the document table untouched")
    void failedExtractionRollsBackCleanly() throws Exception {
        String admin = adminOf("Rollback Firm");
        String projectId = createProject(admin, createClient(admin, "ADQ", "UAE"), "CFO");

        llm.failNext();
        MockMultipartFile file = new MockMultipartFile("file", "broken.txt", "text/plain",
                "some text".getBytes(StandardCharsets.UTF_8));

        MvcResult result = mvc.perform(multipart(positionUrl(projectId) + "/brief-document")
                        .file(file)
                        .header("Authorization", "Bearer " + admin))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(502);
        assertThat(codeOf(result)).isEqualTo("BRIEF_EXTRACTION_FAILED");
        assertThat(documentRows.findAll()).isEmpty();

        mvc.perform(get(positionUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.briefDocument").doesNotExist());
    }

    @Test
    @DisplayName("an oversized file is rejected before any parsing is attempted")
    void oversizedFileIsRejected() throws Exception {
        String admin = adminOf("TooBig Firm");
        String projectId = createProject(admin, createClient(admin, "NMC", "UAE"), "CFO");

        MockMultipartFile file = new MockMultipartFile("file", "long-pd.txt", "text/plain",
                "x".repeat(2048).getBytes(StandardCharsets.UTF_8));

        MvcResult result = mvc.perform(multipart(positionUrl(projectId) + "/brief-document")
                        .file(file)
                        .header("Authorization", "Bearer " + admin))
                .andReturn();
        assertThat(codeOf(result)).isEqualTo("BRIEF_DOCUMENT_TOO_LARGE");
    }

    @Test
    @DisplayName("an unsupported file type is rejected")
    void unsupportedTypeIsRejected() throws Exception {
        String admin = adminOf("WrongType Firm");
        String projectId = createProject(admin, createClient(admin, "Al Rabie", "UAE"), "CFO");

        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png",
                new byte[]{1, 2, 3});

        MvcResult result = mvc.perform(multipart(positionUrl(projectId) + "/brief-document")
                        .file(file)
                        .header("Authorization", "Bearer " + admin))
                .andReturn();
        assertThat(codeOf(result)).isEqualTo("BRIEF_DOCUMENT_UNSUPPORTED_TYPE");
    }

    @Test
    @DisplayName("a locked position refuses an upload")
    void lockedPositionRefusesUpload() throws Exception {
        String admin = adminOf("Locked Firm");
        String projectId = createProject(admin, createClient(admin, "Agthia", "UAE"), "CFO");
        lockPosition(admin, projectId);

        MockMultipartFile file = new MockMultipartFile("file", "pd.txt", "text/plain",
                "text".getBytes(StandardCharsets.UTF_8));

        assertThat(codeOf(mvc.perform(multipart(positionUrl(projectId) + "/brief-document")
                        .file(file)
                        .header("Authorization", "Bearer " + admin))
                .andReturn())).isEqualTo("POSITION_LOCKED");
    }

    @Test
    @DisplayName("Remove clears the file but keeps whatever it already populated")
    void removeClearsFileNotFields() throws Exception {
        String admin = adminOf("Remove Firm");
        String projectId = createProject(admin, createClient(admin, "Fine Hygienic", "UAE"), "CFO");

        llm.respondWith(new BriefExtraction(
                null, null, null, "Group CFO", null, null, null, null,
                null, null, null, null, null, null, null, List.of(),
                List.of(), List.of(), List.of()));
        MockMultipartFile file = new MockMultipartFile("file", "pd.txt", "text/plain",
                "text".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart(positionUrl(projectId) + "/brief-document")
                        .file(file)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportsTo").value("Group CFO"));

        mvc.perform(delete(positionUrl(projectId) + "/brief-document")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.briefDocument").doesNotExist())
                .andExpect(jsonPath("$.reportsTo").value("Group CFO"));
    }

    private static String positionUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/position";
    }

    private void lockPosition(String admin, String projectId) throws Exception {
        mvc.perform(put(positionUrl(projectId) + "/competencies")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"technical":[{"name":"Treasury","weight":100}],
                                 "behavioural":[{"name":"Leadership","weight":100}]}"""))
                .andExpect(status().isOk());
        mvc.perform(put(positionUrl(projectId) + "/criteria")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"criteria":[{"text":"Board experience","mode":"REQUIRED","fromBrief":false}]}"""))
                .andExpect(status().isOk());
        mvc.perform(post(positionUrl(projectId) + "/lock")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
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
