package app.lightmove.api.position;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.StubChatModel;
import app.lightmove.api.core.llm.model.ProductionVersion;
import app.lightmove.api.core.llm.service.PromptRegistryStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

/** #1452 with the registry on: what a document reading filled is counted once, at publish, against its version. */
@IntegrationTest
@TestPropertySource(properties = "lightmove.ai.prompt-registry.enabled=true")
class PositionSuggestionCountIntegrationTest extends FlowTestSupport {

    private static final String DETAILS = "position-extract-details";
    private static final String ASSESSMENT = "position-extract-assessment";

    @Autowired PromptRegistryStore registry;
    @Autowired StubChatModel model;
    @Autowired @FixtureJdbc JdbcTemplate db;

    @AfterEach
    void answerAsUsualAgain() {
        model.reset();
    }

    @Test
    @DisplayName("a model reading names the version that answered it, and the heuristic's names none")
    void aReadingNamesItsVersion() throws Exception {
        String admin = adminOf("Reading Firm");
        String projectId = projectOf(admin);
        attach(admin, projectId);

        assertThat(extract(admin, projectId).get("promptVersion").isNull())
                .as("the stub's usual reply will not bind, so the heuristic reads it").isTrue();

        model.answerWith("""
                {"roleTitle":"Chief Financial Officer","roleTitleSnippet":"Job Title: Chief Financial Officer",
                 "department":"Finance","departmentSnippet":"Department: Finance"}""");
        assertThat(extract(admin, projectId).get("promptVersion").asInt()).isEqualTo(liveVersionOf(DETAILS));
    }

    @Test
    @DisplayName("at publish a filled field still from the document is kept, and one a person edited is changed")
    void publishCountsEachFilledField() throws Exception {
        String admin = adminOf("Publish Firm");
        String projectId = projectOf(admin);
        int version = liveVersionOf(DETAILS);
        long[] before = decisionsOf(DETAILS, version);

        putDetails(admin, projectId, "C_SUITE", "DOCUMENT", "DOCUMENT", version);
        putDetails(admin, projectId, "BOARD", "MANUAL", "DOCUMENT", null);
        publish(admin, projectId);

        long[] after = decisionsOf(DETAILS, version);
        assertThat(after[0] - before[0]).as("the department and the responsibilities, as filled").isEqualTo(2);
        assertThat(after[1] - before[1]).as("the seniority a person changed").isEqualTo(1);
        assertThat(db.queryForObject("""
                        SELECT count(*) FROM app_lm_position_suggested_field s
                        JOIN app_lm_position p ON p.id = s.position_id WHERE p.project_id = ?::uuid
                        """, Long.class, projectId))
                .as("counted once, then forgotten").isZero();

        publish(admin, projectId);
        assertThat(decisionsOf(DETAILS, version)).as("a second publish counts nothing again").isEqualTo(after);
    }

    @Test
    @DisplayName("a list is kept only while every row the reading left still comes from the document")
    void aListWithARowChangedIsChanged() throws Exception {
        String admin = adminOf("List Firm");
        String projectId = projectOf(admin);
        int version = liveVersionOf(ASSESSMENT);
        long[] before = decisionsOf(ASSESSMENT, version);

        putCriteria(admin, projectId, "DOCUMENT", version);
        putCriteria(admin, projectId, "MANUAL", null);
        publish(admin, projectId);

        long[] after = decisionsOf(ASSESSMENT, version);
        assertThat(after[0] - before[0]).isZero();
        assertThat(after[1] - before[1]).isEqualTo(1);
    }

    @Test
    @DisplayName("a save naming no reading records nothing, and a version that never served counts nothing")
    void onlyAReadingThatServedIsCounted() throws Exception {
        String admin = adminOf("Unread Firm");
        String projectId = projectOf(admin);
        int version = liveVersionOf(DETAILS);
        long[] before = decisionsOf(DETAILS, version);

        putDetails(admin, projectId, "C_SUITE", "DOCUMENT", "DOCUMENT", null);
        publish(admin, projectId);
        assertThat(decisionsOf(DETAILS, version)).isEqualTo(before);

        putDetails(admin, projectId, "C_SUITE", "DOCUMENT", "DOCUMENT", 9_999);
        publish(admin, projectId);
        assertThat(decisionsOf(DETAILS, 9_999)).isEqualTo(new long[] {0, 0});
    }

    private void putDetails(String token, String projectId, String seniority, String senioritySource,
                            String responsibilitySource, Integer readingVersion) throws Exception {
        mvc.perform(put(positionUrl(projectId) + "/details")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleTitle":"CFO","department":"Finance","locationCity":null,"locationCountry":null,
                                 "employmentType":null,"seniority":"%s","narrative":null,
                                 "responsibilities":[{"text":"Own the group's reporting","source":"%s"},
                                                     {"text":"Lead the audit","source":"DOCUMENT"}],
                                 "fieldSources":{"department":"DOCUMENT","seniority":"%s"},
                                 "readingVersion":%s}""".formatted(seniority, responsibilitySource,
                                senioritySource, readingVersion)))
                .andExpect(status().isOk());
    }

    private void putCriteria(String token, String projectId, String firstSource, Integer readingVersion)
            throws Exception {
        mvc.perform(put(positionUrl(projectId) + "/criteria")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"criteria":[{"text":"Has led a listing","mode":"REQUIRED","source":"%s"},
                                             {"text":"Arabic speaker","mode":"REQUIRED","source":"DOCUMENT"}],
                                 "readingVersion":%s}""".formatted(firstSource, readingVersion)))
                .andExpect(status().isOk());
    }

    private void publish(String token, String projectId) throws Exception {
        mvc.perform(post(positionUrl(projectId) + "/publish").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private JsonNode extract(String token, String projectId) throws Exception {
        return body(mvc.perform(post(positionUrl(projectId) + "/document/extract/details")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
    }

    private void attach(String token, String projectId) throws Exception {
        mvc.perform(multipart(positionUrl(projectId) + "/document")
                        .file(new MockMultipartFile("file", "brief.txt", "text/plain",
                                "Job Title: Chief Financial Officer\nDepartment: Finance\n".getBytes()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    /** Accepted, then changed, for the version today. */
    private long[] decisionsOf(String promptId, int version) {
        return db.query("""
                        SELECT coalesce(sum(accepted), 0) AS accepted, coalesce(sum(changed), 0) AS changed
                        FROM app_lm_platform_prompt_outcome_daily
                        WHERE prompt_id = ? AND version = ? AND day = (now() AT TIME ZONE 'UTC')::date
                        """, (row, index) -> new long[] {row.getLong("accepted"), row.getLong("changed")},
                promptId, version).getFirst();
    }

    private int liveVersionOf(String promptId) {
        return registry.productionOf(promptId).map(ProductionVersion::version).orElseThrow();
    }

    private String adminOf(String workspaceName) throws Exception {
        createWorkspace(verifiedUser("Alok Kumar", "alok@" + domain), workspaceName);
        return login("alok@" + domain);
    }

    private String projectOf(String token) throws Exception {
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customName\":\"Meridian Holdings\",\"hqCountry\":\"UAE\"}"))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"%s\",\"positionTitle\":\"CFO\"}".formatted(clientId)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private static String positionUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/position";
    }
}
