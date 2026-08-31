package app.lightmove.api.dataimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * Importing a consultant's spreadsheet into a mandate's Companies grid, end to end.
 *
 * <p>The context runs on {@code StubChatModel}, whose fixed reply will not bind to a mapping — so
 * every mapping here comes from {@code HeuristicColumnMatcher}. That is deliberate rather than a
 * limitation: it is the path a user without Application Default Credentials gets, and the one worth
 * proving works against a real database.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class SpreadsheetImportIntegrationTest extends FlowTestSupport {

    @Test
    @DisplayName("a companies-only file lands every row In universe, sourced as an import")
    void importsCompaniesOnly() throws Exception {
        String admin = adminOf("Import Companies Firm");
        String projectId = project(admin);

        JsonNode summary = importFile(admin, projectId, """
                Company,Sector,Country,Headcount
                ACWA Power,Energy,Saudi Arabia,3000
                Agthia Group,Food,UAE,1200
                """);

        assertThat(summary.get("companiesCreated").asInt()).isEqualTo(2);
        assertThat(summary.get("candidatesCreated").asInt()).isZero();

        JsonNode universe = companies(admin, projectId);
        assertThat(universe.get("counts").get("inUniverse").asInt()).isEqualTo(2);
        JsonNode first = universe.get("companies").get(0);
        assertThat(first.get("source").asText()).isEqualTo("csv");
        assertThat(first.get("numEmployees").asInt()).isIn(3000, 1200);
    }

    @Test
    @DisplayName("a candidates-only file maps people with no company at all")
    void importsCandidatesOnly() throws Exception {
        String admin = adminOf("Import People Firm");
        String projectId = project(admin);

        JsonNode summary = importFile(admin, projectId, """
                Full Name,Job Title,Work Email
                Layla Haddad,Chief Financial Officer,layla@acwa.example
                """);

        assertThat(summary.get("candidatesCreated").asInt()).isEqualTo(1);
        assertThat(summary.get("companiesCreated").asInt()).isZero();

        JsonNode people = candidates(admin, projectId);
        assertThat(people.get("candidates").get(0).get("fullName").asText()).isEqualTo("Layla Haddad");
        // V36's whole point: a researcher meets people at companies the universe does not carry.
        assertThat(people.get("candidates").get(0).get("triageCompanyId").isNull()).isTrue();
        assertThat(people.get("candidates").get(0).get("source").asText()).isEqualTo("csv");
    }

    @Test
    @DisplayName("a combined file maps each person to the company on their own row")
    void importsCompaniesAndPeopleTogether() throws Exception {
        String admin = adminOf("Import Both Firm");
        String projectId = project(admin);

        JsonNode summary = importFile(admin, projectId, """
                Company,Full Name,Job Title
                ACWA Power,Layla Haddad,CFO
                ACWA Power,Omar Nasser,COO
                Agthia Group,Sara Mansour,CEO
                """);

        // Two people at one company is two rows in the file and one company in the universe — the
        // grid's own row shape, and the reason the import cannot just count rows.
        assertThat(summary.get("companiesCreated").asInt()).isEqualTo(2);
        assertThat(summary.get("candidatesCreated").asInt()).isEqualTo(3);

        JsonNode people = candidates(admin, projectId);
        assertThat(people.get("candidates")).allSatisfy(person ->
                assertThat(person.get("triageCompanyId").isNull()).isFalse());
    }

    @Test
    @DisplayName("re-importing a corrected file updates the rows rather than duplicating them")
    void reImportUpdatesRatherThanDuplicating() throws Exception {
        String admin = adminOf("Import Reimport Firm");
        String projectId = project(admin);
        importFile(admin, projectId, """
                Company,Headcount,Full Name,Job Title
                ACWA Power,3000,Layla Haddad,CFO
                """);

        JsonNode second = importFile(admin, projectId, """
                Company,Headcount,Full Name,Job Title
                ACWA Power,3500,Layla Haddad,Group CFO
                """);

        assertThat(second.get("companiesCreated").asInt()).isZero();
        assertThat(second.get("companiesUpdated").asInt()).isEqualTo(1);
        assertThat(second.get("candidatesUpdated").asInt()).isEqualTo(1);

        assertThat(companies(admin, projectId).get("totalCount").asInt()).isEqualTo(1);
        assertThat(companies(admin, projectId).get("companies").get(0).get("numEmployees").asInt())
                .isEqualTo(3500);
        assertThat(candidates(admin, projectId).get("candidates").get(0).get("title").asText())
                .isEqualTo("Group CFO");
    }

    @Test
    @DisplayName("a blank cell in a later file does not clear what the first one stored")
    void aBlankCellNeverClearsAStoredValue() throws Exception {
        String admin = adminOf("Import Blank Firm");
        String projectId = project(admin);
        importFile(admin, projectId, """
                Company,Sector,Headcount
                ACWA Power,Energy,3000
                """);

        // The second file is a name-and-country list. It knows nothing about headcount, which is not
        // the same as knowing the headcount is nothing.
        importFile(admin, projectId, """
                Company,Country
                ACWA Power,Saudi Arabia
                """);

        JsonNode company = companies(admin, projectId).get("companies").get(0);
        assertThat(company.get("numEmployees").asInt()).isEqualTo(3000);
        assertThat(company.get("industry").asText()).isEqualTo("Energy");
        assertThat(company.get("companyCountry").asText()).isEqualTo("Saudi Arabia");
    }

    @Test
    @DisplayName("a header no field covers becomes a project column, and its values land on the rows")
    void anUnknownHeaderBecomesACustomColumn() throws Exception {
        String admin = adminOf("Import Custom Firm");
        String projectId = project(admin);

        JsonNode summary = importFile(admin, projectId, """
                Company,Full Name,Ethnicity
                ACWA Power,Layla Haddad,Lebanese
                """);

        assertThat(summary.get("customColumnsCreated")).hasSize(1);
        assertThat(summary.get("customColumnsCreated").get(0).asText()).isEqualTo("Ethnicity");

        JsonNode columns = body(mvc.perform(get("/api/v1/projects/" + projectId + "/custom-columns")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn()).get("columns");
        assertThat(columns).hasSize(1);
        String fieldKey = columns.get(0).get("fieldKey").asText();
        assertThat(fieldKey).isEqualTo("ethnicity");
        assertThat(columns.get(0).get("target").asText()).isEqualTo("candidate");

        JsonNode person = candidates(admin, projectId).get("candidates").get(0);
        assertThat(person.get("customFields").get(fieldKey).asText()).isEqualTo("Lebanese");
    }

    @Test
    @DisplayName("importing the same extra header twice fills the column rather than making a second")
    void reusesACustomColumnAcrossImports() throws Exception {
        String admin = adminOf("Import Custom Twice Firm");
        String projectId = project(admin);
        importFile(admin, projectId, """
                Company,Full Name,Ethnicity
                ACWA Power,Layla Haddad,Lebanese
                """);

        JsonNode second = importFile(admin, projectId, """
                Company,Full Name,Ethnicity
                Agthia Group,Omar Nasser,Emirati
                """);

        assertThat(second.get("customColumnsCreated")).isEmpty();
        JsonNode columns = body(mvc.perform(get("/api/v1/projects/" + projectId + "/custom-columns")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn()).get("columns");
        assertThat(columns).hasSize(1);
    }

    @Test
    @DisplayName("a row with neither a company nor a person is reported, and the rest still import")
    void reportsABadRowWithoutLosingTheOthers() throws Exception {
        String admin = adminOf("Import Bad Row Firm");
        String projectId = project(admin);

        JsonNode summary = importFile(admin, projectId, """
                Company,Sector
                ACWA Power,Energy
                ,Food
                Agthia Group,Food
                """);

        assertThat(summary.get("companiesCreated").asInt()).isEqualTo(2);
        assertThat(summary.get("rowErrors")).hasSize(1);
        // Counted from the file's first data row, as a person reading it in Excel would.
        assertThat(summary.get("rowErrors").get(0).get("rowNumber").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("the preview writes nothing")
    void previewIsARead() throws Exception {
        String admin = adminOf("Import Preview Firm");
        String projectId = project(admin);

        JsonNode preview = body(mvc.perform(multipart("/api/v1/projects/" + projectId + "/import/preview")
                        .file(csv("""
                                Company,Full Name
                                ACWA Power,Layla Haddad
                                """))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(preview.get("rowCount").asInt()).isEqualTo(1);
        assertThat(preview.get("columns")).hasSize(2);
        assertThat(preview.get("columns").get(0).get("mapping").get("targetField").asText())
                .isEqualTo("companyName");
        assertThat(preview.get("availableFields").size()).isGreaterThan(10);
        assertThat(companies(admin, projectId).get("totalCount").asInt()).isZero();
    }

    @Test
    @DisplayName("another workspace's project is not found, not forbidden")
    void refusesAProjectOutsideTheCallersWorkspace() throws Exception {
        String owner = adminOf("Import Tenant Firm");
        String projectId = project(owner);

        createWorkspace(verifiedUser("Nadia Rahman", "nadia@other-" + domain), "Other Firm");
        String outsider = login("nadia@other-" + domain);

        mvc.perform(multipart("/api/v1/projects/" + projectId + "/import/preview")
                        .file(csv("Company\nACWA Power\n"))
                        .header("Authorization", "Bearer " + outsider))
                // 404 rather than 403 on purpose: ProjectAccess scopes the project to the caller's
                // workspace before it looks at any seat, so a foreign id is never confirmed to exist.
                .andExpect(status().isNotFound());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Previews, then commits the mapping the preview proposed — what the dialog does when nobody edits it. */
    private JsonNode importFile(String token, String projectId, String content) throws Exception {
        JsonNode preview = body(mvc.perform(multipart("/api/v1/projects/" + projectId + "/import/preview")
                        .file(csv(content))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());

        var columns = new java.util.ArrayList<JsonNode>();
        preview.get("columns").forEach(column -> columns.add(column.get("mapping")));
        String mapping = json.writeValueAsString(java.util.Map.of("columns", columns));

        MvcResult committed = mvc.perform(multipart("/api/v1/projects/" + projectId + "/import/commit")
                        .file(csv(content))
                        .file(new MockMultipartFile("mapping", "mapping.json",
                                MediaType.APPLICATION_JSON_VALUE, mapping.getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return body(committed);
    }

    private JsonNode companies(String token, String projectId) throws Exception {
        return body(mvc.perform(get("/api/v1/projects/" + projectId + "/triage?status=inUniverse")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
    }

    private JsonNode candidates(String token, String projectId) throws Exception {
        return body(mvc.perform(get("/api/v1/projects/" + projectId + "/candidates")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
    }

    private static MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "longlist.csv", "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }

    private String adminOf(String workspaceName) throws Exception {
        createWorkspace(verifiedUser("Alok Kumar", "alok@" + domain), workspaceName);
        return login("alok@" + domain);
    }

    private String project(String token) throws Exception {
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Import Client %s"}"""
                                .formatted(java.util.UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"Head of Retail"}
                                """.formatted(clientId)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }
}
