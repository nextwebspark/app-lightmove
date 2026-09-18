package app.lightmove.api.dataexport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

/**
 * Downloading a mandate's Companies grid. The file is the grid — same rows, same columns, same
 * pairing of people to companies — so most of what is asserted here is that it did not quietly
 * become something narrower.
 */
@IntegrationTest
class CompaniesExportIntegrationTest extends FlowTestSupport {

    @Autowired JdbcTemplate db;

    @Test
    @DisplayName("carries the grid's own headers, then this mandate's own columns")
    void carriesEveryColumn() throws Exception {
        String admin = adminOf("Export Headers Firm");
        String projectId = project(admin);
        defineColumn(admin, projectId, "candidate", "Ethnicity");

        List<String> headers = headersOf(export(admin, projectId, null, null));

        assertThat(headers).startsWith("Company", "Website", "Company LinkedIn", "Country",
                "Executive", "Title", "Email", "Phone", "Status");
        assertThat(headers).endsWith("Ethnicity");
    }

    @Test
    @DisplayName("a company with two executives is two lines, with the company repeated")
    void writesOneLinePerPersonAtACompany() throws Exception {
        String admin = adminOf("Export Rows Firm");
        String projectId = project(admin);
        String companyId = capture(admin, projectId, "ACWA Power");
        map(admin, projectId, companyId, "Layla Haddad");
        map(admin, projectId, companyId, "Omar Said");

        List<List<String>> rows = rowsOf(export(admin, projectId, null, null));

        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(row -> assertThat(row.get(0)).isEqualTo("ACWA Power"));
        assertThat(rows.stream().map(row -> row.get(4))).containsExactlyInAnyOrder("Layla Haddad", "Omar Said");
    }

    @Test
    @DisplayName("a company nobody is mapped at still gets a line")
    void keepsACompanyWithNoExecutive() throws Exception {
        String admin = adminOf("Export Empty Company Firm");
        String projectId = project(admin);
        capture(admin, projectId, "Gulf Industrial");

        List<List<String>> rows = rowsOf(export(admin, projectId, null, null));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get(0)).isEqualTo("Gulf Industrial");
        assertThat(rows.get(0).get(4)).isEmpty();
    }

    @Test
    @DisplayName("an executive at no company of the mandate rides the universe, and no other stage")
    void carriesUnmappedExecutivesOnTheUniverseOnly() throws Exception {
        String admin = adminOf("Export Unmapped Firm");
        String projectId = project(admin);
        String companyId = capture(admin, projectId, "ACWA Power");
        mapUnmapped(admin, projectId, "Yasmin Farouk", "Masdar");
        move(admin, projectId, companyId, "shortlisted");

        assertThat(rowsOf(export(admin, projectId, null, null)))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.get(0)).isEqualTo("Masdar");
                    assertThat(row.get(4)).isEqualTo("Yasmin Farouk");
                });
        assertThat(rowsOf(export(admin, projectId, "shortlisted", null)))
                .singleElement()
                .satisfies(row -> assertThat(row.get(0)).isEqualTo("ACWA Power"));
    }

    @Test
    @DisplayName("the search box narrows the file, and takes the unmapped rows with it")
    void honoursTheSearchBox() throws Exception {
        String admin = adminOf("Export Search Firm");
        String projectId = project(admin);
        capture(admin, projectId, "ACWA Power");
        capture(admin, projectId, "Gulf Industrial");
        mapUnmapped(admin, projectId, "Yasmin Farouk", "Masdar");

        assertThat(rowsOf(export(admin, projectId, null, null))).hasSize(3);

        List<List<String>> narrowed = rowsOf(export(admin, projectId, null, "acwa"));
        assertThat(narrowed).singleElement()
                .satisfies(row -> assertThat(row.get(0)).isEqualTo("ACWA Power"));
    }

    @Test
    @DisplayName("the Executive and Status header filters narrow the file, rows as well as companies")
    void honoursTheExecutiveHeaderFilters() throws Exception {
        String admin = adminOf("Export Executive Filter Firm");
        String projectId = project(admin);
        String companyId = capture(admin, projectId, "ACWA Power");
        map(admin, projectId, companyId, "Layla Haddad", "contacted");
        map(admin, projectId, companyId, "Omar Said", "identified");

        // The server's filter is company-level — does this company hold a matching executive at all —
        // so a file that stopped there would keep the company and draw Omar's line beside Layla's.
        List<List<String>> contacted = rowsOf(exportFiltered(admin, projectId, null, null, "contacted"));
        assertThat(contacted).singleElement()
                .satisfies(row -> assertThat(row.get(4)).isEqualTo("Layla Haddad"));

        List<List<String>> named = rowsOf(exportFiltered(admin, projectId, "omar", null, null));
        assertThat(named).singleElement()
                .satisfies(row -> assertThat(row.get(4)).isEqualTo("Omar Said"));
    }

    @Test
    @DisplayName("an unknown executive status is refused rather than quietly ignored")
    void refusesAnUnknownExecutiveStatus() throws Exception {
        String admin = adminOf("Export Bad Status Firm");

        mvc.perform(get(exportUrl(project(admin))).param("executiveStatuses", "pending")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("carries every page of the stage, not the one the grid is showing")
    void carriesTheWholeStage() throws Exception {
        String admin = adminOf("Export Whole Stage Firm");
        String projectId = project(admin);
        for (int index = 0; index < 30; index++) {
            capture(admin, projectId, "Company %02d".formatted(index));
        }

        // The grid's own default page is 25 — an export that read one page would stop there.
        assertThat(rowsOf(export(admin, projectId, null, null))).hasSize(30);
    }

    @Test
    @DisplayName("custom values land under their own column, on whichever half of the row owns them")
    void carriesCustomValues() throws Exception {
        String admin = adminOf("Export Custom Values Firm");
        String projectId = project(admin);
        String columnId = defineColumn(admin, projectId, "company", "Tier");
        String fieldKey = fieldKeyOf(admin, projectId, columnId);
        String companyId = capture(admin, projectId, "ACWA Power");
        mvc.perform(patch("/api/v1/projects/" + projectId + "/triage/" + companyId + "/custom-fields")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customFields":{"%s":"A"}}""".formatted(fieldKey)))
                .andExpect(status().isOk());

        String csv = export(admin, projectId, null, null);

        assertThat(headersOf(csv)).endsWith("Tier");
        assertThat(rowsOf(csv).get(0)).endsWith("A");
    }

    @Test
    @DisplayName("served as a csv attachment the browser will not sniff")
    void servesADownload() throws Exception {
        String admin = adminOf("Export Headers Wire Firm");

        mvc.perform(get(exportUrl(project(admin))).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @DisplayName("an unknown stage is refused rather than quietly exported as the universe")
    void refusesAnUnknownStage() throws Exception {
        String admin = adminOf("Export Bad Stage Firm");

        mvc.perform(get(exportUrl(project(admin)) + "?status=archived")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("records who took a mandate's data out of the product")
    void auditsTheExport() throws Exception {
        String admin = adminOf("Export Audit Firm");
        String projectId = project(admin);
        capture(admin, projectId, "ACWA Power");

        export(admin, projectId, null, null);

        Long recorded = db.queryForObject(
                "select count(*) from app_lm_audit_event where event_type = 'COMPANIES_EXPORTED'"
                        + " and target_id = ?", Long.class, projectId);
        assertThat(recorded).isEqualTo(1L);
    }

    @Test
    @DisplayName("reading it follows the seat, and a client representative may take their mandate's file")
    void followsTheReadGate() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Export Gate Firm");
        String admin = login(alok);

        String sara = "sara@" + domain;
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");

        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Export Client"}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        String projectId = body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"Head of Retail"}
                                """.formatted(clientId)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();

        // A member with no seat on this mandate reads nothing of it, file included.
        mvc.perform(get(exportUrl(projectId)).header("Authorization", "Bearer " + login(sara)))
                .andExpect(status().isForbidden());

        // WORK_VIEW, not the WORK_EXECUTE the import template carries: a representative may take the
        // mandate they can already read on screen, and still may not write to it.
        String repEmail = "ext@export-client.example";
        JsonNode representative = body(mvc.perform(
                        post("/api/v1/clients/" + clientId + "/representatives")
                                .header("Authorization", "Bearer " + admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"fullName":"Ext Rep","position":"Chair","email":"%s"}
                                        """.formatted(repEmail)))
                .andExpect(status().isCreated())
                .andReturn());
        String rep = body(mvc.perform(post("/api/v1/onboarding/accept-invitation-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","fullName":"Ext Rep","password":"%s"}
                                """.formatted(email.latestTokenFor(repEmail), PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn()).get("accessToken").asText();
        mvc.perform(post("/api/v1/projects/" + projectId + "/representatives")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"representativeId":"%s"}
                                """.formatted(representative.get("id").asText())))
                .andExpect(status().isOk());

        mvc.perform(get(exportUrl(projectId)).header("Authorization", "Bearer " + rep))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/projects/" + projectId + "/import/template")
                        .header("Authorization", "Bearer " + rep))
                .andExpect(status().isForbidden());
    }

    // fixture

    private static String exportUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/export/companies";
    }

    private String export(String token, String projectId, String stage, String query) throws Exception {
        MockHttpServletRequestBuilder request = get(exportUrl(projectId))
                .header("Authorization", "Bearer " + token);
        if (stage != null) {
            request = request.param("status", stage);
        }
        if (query != null) {
            request = request.param("q", query);
        }
        return mvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String exportFiltered(String token, String projectId, String executiveQuery,
                                  String query, String executiveStatus) throws Exception {
        MockHttpServletRequestBuilder request = get(exportUrl(projectId))
                .header("Authorization", "Bearer " + token);
        if (query != null) {
            request = request.param("q", query);
        }
        if (executiveQuery != null) {
            request = request.param("executiveQuery", executiveQuery);
        }
        if (executiveStatus != null) {
            request = request.param("executiveStatuses", executiveStatus);
        }
        return mvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String adminOf(String firmName) throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), firmName);
        return login(alok);
    }

    private String project(String admin) throws Exception {
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Export Client %s"}""".formatted(java.util.UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"Head of Retail"}
                                """.formatted(clientId)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private String capture(String token, String projectId, String companyName) throws Exception {
        return body(mvc.perform(post("/api/v1/projects/" + projectId + "/triage/capture")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"%s"}""".formatted(companyName)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private void move(String token, String projectId, String companyId, String status) throws Exception {
        mvc.perform(patch("/api/v1/projects/" + projectId + "/triage/" + companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"%s"}""".formatted(status)))
                .andExpect(status().isOk());
    }

    private void map(String token, String projectId, String companyId, String fullName) throws Exception {
        map(token, projectId, companyId, fullName, "identified");
    }

    private void map(String token, String projectId, String companyId, String fullName, String status)
            throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectId + "/candidates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"triageCompanyId":"%s","fullName":"%s","status":"%s"}"""
                                .formatted(companyId, fullName, status)))
                .andExpect(status().isCreated());
    }

    private void mapUnmapped(String token, String projectId, String fullName, String employer)
            throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectId + "/candidates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"%s","employerName":"%s"}""".formatted(fullName, employer)))
                .andExpect(status().isCreated());
    }

    private String defineColumn(String token, String projectId, String target, String label)
            throws Exception {
        return body(mvc.perform(post("/api/v1/projects/" + projectId + "/custom-columns")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"target":"%s","label":"%s","dataType":"text"}"""
                                .formatted(target, label)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private String fieldKeyOf(String token, String projectId, String columnId) throws Exception {
        JsonNode columns = body(mvc.perform(get("/api/v1/projects/" + projectId + "/custom-columns")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()).get("columns");
        for (JsonNode column : columns) {
            if (column.get("id").asText().equals(columnId)) {
                return column.get("fieldKey").asText();
            }
        }
        throw new AssertionError("no column " + columnId);
    }

    private static List<String> headersOf(String csv) {
        return cellsOf(linesOf(csv).get(0));
    }

    private static List<List<String>> rowsOf(String csv) {
        return linesOf(csv).stream().skip(1).map(CompaniesExportIntegrationTest::cellsOf).toList();
    }

    private static List<String> linesOf(String csv) {
        return csv.replace("﻿", "").lines().toList();
    }

    /** Splits one line the way a CSV reader would, honouring the quoting. */
    private static List<String> cellsOf(String line) {
        return Arrays.stream(line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1))
                .map(value -> value.startsWith("\"") && value.endsWith("\"") && value.length() > 1
                        ? value.substring(1, value.length() - 1).replace("\"\"", "\"")
                        : value)
                .toList();
    }
}
