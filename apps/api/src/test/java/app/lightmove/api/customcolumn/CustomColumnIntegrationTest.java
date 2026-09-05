package app.lightmove.api.customcolumn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

/**
 * The columns a mandate adds to its own grid: defining them, editing them, ordering them, removing
 * them, and the values rows store under them.
 *
 * <p>Two behaviours carry most of the weight. A column's <b>key never moves</b> when its label is
 * renamed, because every value already stored points at it. And deleting a column takes the
 * definition and <b>not the data</b>, so one misclick is recoverable.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class CustomColumnIntegrationTest extends FlowTestSupport {

    @Test
    @DisplayName("a fresh mandate has no custom columns")
    void freshProjectHasNoCustomColumns() throws Exception {
        String admin = adminOf("Columns Empty Firm");
        String projectId = project(admin);

        mvc.perform(get(columnsUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns").isEmpty());
    }

    @Test
    @DisplayName("defining a column slugs a key from the label and appends it to its grid")
    void definesAColumn() throws Exception {
        String admin = adminOf("Columns Define Firm");
        String projectId = project(admin);

        JsonNode defined = define(admin, projectId, "candidate", "Ethnicity / Origin", "text");

        assertThat(defined.get("fieldKey").asText()).isEqualTo("ethnicityOrigin");
        assertThat(defined.get("label").asText()).isEqualTo("Ethnicity / Origin");
        assertThat(defined.get("displayOrder").asInt()).isZero();
        assertThat(defined.get("hidden").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("two columns on one grid cannot share a name, however it is cased")
    void refusesADuplicateLabel() throws Exception {
        String admin = adminOf("Columns Duplicate Firm");
        String projectId = project(admin);
        define(admin, projectId, "candidate", "Ethnicity", "text");

        mvc.perform(post(columnsUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"target":"candidate","label":"ethnicity","dataType":"text"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CUSTOM_COLUMN_NAME_TAKEN"));
    }

    @Test
    @DisplayName("the same name on the other grid is not a duplicate")
    void allowsTheSameNameOnTheOtherGrid() throws Exception {
        // "Region" means the company's region on one half of the row and the person's on the other.
        String admin = adminOf("Columns Both Grids Firm");
        String projectId = project(admin);
        define(admin, projectId, "candidate", "Region", "text");

        JsonNode onCompany = define(admin, projectId, "company", "Region", "text");

        assertThat(onCompany.get("target").asText()).isEqualTo("company");
    }

    @Test
    @DisplayName("renaming a column moves its label and never its key")
    void renamingKeepsTheKey() throws Exception {
        // The key is what every stored value points at. If a rename moved it, fixing a typo in a
        // header would empty the column for every row already imported.
        String admin = adminOf("Columns Rename Firm");
        String projectId = project(admin);
        JsonNode defined = define(admin, projectId, "candidate", "Ethnicty", "text");

        JsonNode renamed = body(mvc.perform(patch(columnsUrl(projectId) + "/" + defined.get("id").asText())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"Ethnicity"}"""))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(renamed.get("label").asText()).isEqualTo("Ethnicity");
        assertThat(renamed.get("fieldKey").asText()).isEqualTo(defined.get("fieldKey").asText());
    }

    @Test
    @DisplayName("hiding a column takes it off the grid and keeps its values")
    void hidingIsNotDeleting() throws Exception {
        String admin = adminOf("Columns Hide Firm");
        String projectId = project(admin);
        JsonNode column = define(admin, projectId, "company", "Ownership", "text");
        String companyId = captureCompany(admin, projectId, "ACWA Power",
                """
                {"%s":"State-owned"}""".formatted(column.get("fieldKey").asText()));

        mvc.perform(patch(columnsUrl(projectId) + "/" + column.get("id").asText())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hidden":true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hidden").value(true));

        assertThat(companyById(admin, projectId, companyId)
                .get("customFields").get(column.get("fieldKey").asText()).asText())
                .isEqualTo("State-owned");
    }

    @Test
    @DisplayName("reordering applies the whole new order")
    void reordersColumns() throws Exception {
        String admin = adminOf("Columns Reorder Firm");
        String projectId = project(admin);
        JsonNode first = define(admin, projectId, "candidate", "Alpha", "text");
        JsonNode second = define(admin, projectId, "candidate", "Beta", "text");

        JsonNode reordered = body(mvc.perform(put(columnsUrl(projectId) + "/order")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"columnIds":["%s","%s"]}"""
                                .formatted(second.get("id").asText(), first.get("id").asText())))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(reordered.get("columns").get(0).get("label").asText()).isEqualTo("Beta");
        assertThat(reordered.get("columns").get(1).get("label").asText()).isEqualTo("Alpha");
    }

    @Test
    @DisplayName("a reorder that leaves a column out is refused rather than half-applied")
    void refusesAPartialReorder() throws Exception {
        // Only the ids sent get renumbered, so a stale tab's short list would leave the column it
        // omitted on a position one of the others just took — a collision nothing in the schema
        // catches, since display_order is deliberately not unique.
        String admin = adminOf("Columns Partial Firm");
        String projectId = project(admin);
        JsonNode first = define(admin, projectId, "candidate", "Alpha", "text");
        define(admin, projectId, "candidate", "Beta", "text");

        mvc.perform(put(columnsUrl(projectId) + "/order")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"columnIds":["%s"]}""".formatted(first.get("id").asText())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a reorder naming one column twice is refused")
    void refusesADuplicateInAReorder() throws Exception {
        String admin = adminOf("Columns Duplicate Firm");
        String projectId = project(admin);
        JsonNode first = define(admin, projectId, "candidate", "Alpha", "text");
        define(admin, projectId, "candidate", "Beta", "text");

        String id = first.get("id").asText();
        mvc.perform(put(columnsUrl(projectId) + "/order")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"columnIds":["%s","%s"]}""".formatted(id, id)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("one grid's reorder cannot renumber the other's columns")
    void refusesAMixedReorder() throws Exception {
        String admin = adminOf("Columns Mixed Firm");
        String projectId = project(admin);
        JsonNode onCandidate = define(admin, projectId, "candidate", "Alpha", "text");
        JsonNode onCompany = define(admin, projectId, "company", "Beta", "text");

        mvc.perform(put(columnsUrl(projectId) + "/order")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"columnIds":["%s","%s"]}"""
                                .formatted(onCandidate.get("id").asText(), onCompany.get("id").asText())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("deleting a column removes the definition and leaves the values behind")
    void deletingKeepsTheValues() throws Exception {
        // What makes an accidental delete recoverable: define the column again under the same name
        // and the rows still hold what was imported into it.
        String admin = adminOf("Columns Delete Firm");
        String projectId = project(admin);
        JsonNode column = define(admin, projectId, "company", "Ownership", "text");
        String fieldKey = column.get("fieldKey").asText();
        String companyId = captureCompany(admin, projectId, "ACWA Power",
                """
                {"%s":"State-owned"}""".formatted(fieldKey));

        mvc.perform(delete(columnsUrl(projectId) + "/" + column.get("id").asText())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        assertThat(companyById(admin, projectId, companyId).get("customFields").get(fieldKey).asText())
                .isEqualTo("State-owned");

        JsonNode redefined = define(admin, projectId, "company", "Ownership", "text");
        assertThat(redefined.get("fieldKey").asText()).isEqualTo(fieldKey);
    }

    @Test
    @DisplayName("a key the mandate has not defined is dropped rather than stored")
    void refusesAnUndefinedKey() throws Exception {
        // The bag is open, so this is the only thing between it and arbitrary caller-chosen keys.
        String admin = adminOf("Columns Undefined Firm");
        String projectId = project(admin);

        String companyId = captureCompany(admin, projectId, "ACWA Power",
                """
                {"somethingNobodyDefined":"x"}""");

        assertThat(companyById(admin, projectId, companyId).get("customFields")).isEmpty();
    }

    @Test
    @DisplayName("a value is checked against its column's type")
    void checksAValueAgainstItsType() throws Exception {
        String admin = adminOf("Columns Type Firm");
        String projectId = project(admin);
        JsonNode column = define(admin, projectId, "company", "Founded Decade", "number");

        mvc.perform(post("/api/v1/projects/" + projectId + "/triage/capture")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"ACWA Power","customFields":{"%s":"not a number"}}"""
                                .formatted(column.get("fieldKey").asText())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("a number keeps its meaning and loses its grouping commas")
    void normalisesANumber() throws Exception {
        String admin = adminOf("Columns Number Firm");
        String projectId = project(admin);
        JsonNode column = define(admin, projectId, "company", "Sites", "number");
        String fieldKey = column.get("fieldKey").asText();

        String companyId = captureCompany(admin, projectId, "ACWA Power",
                """
                {"%s":"1,200"}""".formatted(fieldKey));

        assertThat(companyById(admin, projectId, companyId).get("customFields").get(fieldKey).asText())
                .isEqualTo("1200");
    }

    @Test
    @DisplayName("another workspace's project has no columns to read")
    void refusesAProjectOutsideTheCallersWorkspace() throws Exception {
        String owner = adminOf("Columns Tenant Firm");
        String projectId = project(owner);

        createWorkspace(verifiedUser("Nadia Rahman", "nadia@other-" + domain), "Other Firm");
        String outsider = login("nadia@other-" + domain);

        // 404 rather than 403: ProjectAccess scopes the project to the caller's workspace before it
        // looks at any seat, so a foreign id is never confirmed to exist.
        mvc.perform(get(columnsUrl(projectId)).header("Authorization", "Bearer " + outsider))
                .andExpect(status().isNotFound());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String columnsUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/custom-columns";
    }

    private JsonNode define(String token, String projectId, String target, String label, String type)
            throws Exception {
        return body(mvc.perform(post(columnsUrl(projectId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"target":"%s","label":"%s","dataType":"%s"}"""
                                .formatted(target, label, type)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private String captureCompany(String token, String projectId, String name, String customFields)
            throws Exception {
        return body(mvc.perform(post("/api/v1/projects/" + projectId + "/triage/capture")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"%s","customFields":%s}""".formatted(name, customFields)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private JsonNode companyById(String token, String projectId, String companyId) throws Exception {
        JsonNode page = body(mvc.perform(get("/api/v1/projects/" + projectId + "/triage?status=inUniverse")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
        for (JsonNode company : page.get("companies")) {
            if (company.get("id").asText().equals(companyId)) {
                return company;
            }
        }
        throw new AssertionError(companyId + " not in the universe: " + page);
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
                                {"customName":"Columns Client %s"}""".formatted(UUID.randomUUID())))
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
