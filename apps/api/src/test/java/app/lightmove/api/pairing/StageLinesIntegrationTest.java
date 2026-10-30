package app.lightmove.api.pairing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.ApolloUniverse;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.triagecompany.constant.TriageCompanySource;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.repository.TriageCompanyWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/** The Companies grid's lines: one list of people at companies, ordered by the column a reader sorted. */
@IntegrationTest
class StageLinesIntegrationTest extends FlowTestSupport {

    @Autowired JdbcTemplate db;
    @Autowired TriageCompanyWriter writer;

    @BeforeEach
    void freshUniverse() {
        new ApolloUniverse(db).reset();
    }

    @Test
    @DisplayName("sorting by executive orders the people themselves, whatever company they sit at, empty slots last")
    void executiveSortOrdersPeopleAcrossCompanies() throws Exception {
        String admin = adminOf("Lines Executive Firm");
        String projectId = project(admin);
        seed(projectId, row("a1", "Alpha Industrial", "Saudi Arabia"), row("a2", "Zenith Holdings", "Saudi Arabia"),
                row("a3", "Nobody Mapped", "Saudi Arabia"));
        mapExecutive(admin, projectId, companyNamed(projectId, "Alpha Industrial"), "zara Khan", null);
        mapExecutive(admin, projectId, companyNamed(projectId, "Zenith Holdings"), "Omar Said", null);
        mapExecutive(admin, projectId, companyNamed(projectId, "Zenith Holdings"), "Bilal Ahmed", null);

        assertThat(executivesOf(lines(admin, projectId, "executive", "asc")))
                .containsExactly("Bilal Ahmed", "Omar Said", "zara Khan", "—");
        assertThat(executivesOf(lines(admin, projectId, "executive", "desc")))
                .containsExactly("zara Khan", "Omar Said", "Bilal Ahmed", "—");
    }

    @Test
    @DisplayName("company names sort as a reader reads them: case aside, numbers by value, quotes not read")
    void companyNamesSortNaturally() throws Exception {
        String admin = adminOf("Lines Natural Firm");
        String projectId = project(admin);
        seed(projectId, row("a1", "Fund 10", "Qatar"), row("a2", "fund 2", "Qatar"), row("a3", "\"Aramco\"", "Qatar"),
                row("a4", "bahri", "Qatar"));

        assertThat(companiesOf(lines(admin, projectId, "name", "asc")))
                .containsExactly("\"Aramco\"", "bahri", "fund 2", "Fund 10");
    }

    @Test
    @DisplayName("Country sorts by where the line's executive is, falling back to the company's headquarters")
    void countrySortsByTheExecutivesOwnPlace() throws Exception {
        String admin = adminOf("Lines Country Firm");
        String projectId = project(admin);
        seed(projectId, row("a1", "Alpha Industrial", "Saudi Arabia"), row("a2", "Beta Trading", "Saudi Arabia"));
        mapExecutive(admin, projectId, companyNamed(projectId, "Alpha Industrial"), "Dubai Based", "United Arab Emirates");
        mapExecutive(admin, projectId, companyNamed(projectId, "Beta Trading"), "Riyadh Based", null);

        assertThat(executivesOf(lines(admin, projectId, "country", "desc")))
                .containsExactly("Dubai Based", "Riyadh Based");
    }

    @Test
    @DisplayName("a staff read carries each company's note count and who filed it beside the page")
    void staffReadCarriesNotesAndAdders() throws Exception {
        String admin = adminOf("Lines Staff Firm");
        String projectId = project(admin);
        seed(projectId, row("a1", "Alpha Industrial", "Oman"));
        mapExecutive(admin, projectId, companyNamed(projectId, "Alpha Industrial"), "Huda Rashid", null);

        JsonNode page = lines(admin, projectId, "note", "desc");
        assertThat(page.at("/lines/0/company/noteCount").asInt()).isZero();
        assertThat(page.at("/lines/0/company/addedByName").asText()).isEqualTo("Alok Kumar");
        String candidateId = page.at("/lines/0/candidate/id").asText();
        assertThat(page.at("/addedBy/" + candidateId + "/name").asText()).isEqualTo("Alok Kumar");
        assertThat(page.get("totalCount").asInt()).isEqualTo(1);
        assertThat(page.get("truncated").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("a sort or direction outside the allowlist is refused, a custom column the mandate lacks included")
    void unknownSortRefused() throws Exception {
        String admin = adminOf("Lines Refusal Firm");
        String projectId = project(admin);

        for (String[] request : List.of(new String[] {"; DROP TABLE x", "asc"}, new String[] {"name", "sideways"},
                new String[] {"custom:company:ethnicity", "asc"})) {
            mvc.perform(get(linesUrl(projectId)).param("sort", request[0]).param("direction", request[1])
                            .header("Authorization", "Bearer " + admin))
                    .andExpect(status().isBadRequest());
        }
    }

    private JsonNode lines(String token, String projectId, String sort, String direction) throws Exception {
        return body(mvc.perform(get(linesUrl(projectId)).param("sort", sort).param("direction", direction)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
    }

    private static List<String> executivesOf(JsonNode page) {
        List<String> names = new ArrayList<>();
        page.get("lines").forEach(line -> names.add(line.get("candidate").isNull()
                ? "—" : line.at("/candidate/fullName").asText()));
        return names;
    }

    private static List<String> companiesOf(JsonNode page) {
        List<String> names = new ArrayList<>();
        page.get("lines").forEach(line -> names.add(line.at("/company/companyName").asText()));
        return names;
    }

    private void mapExecutive(String token, String projectId, String triageCompanyId, String fullName,
                              String locationCountry) throws Exception {
        String country = locationCountry == null ? "" : ",\"locationCountry\":\"%s\"".formatted(locationCountry);
        mvc.perform(post("/api/v1/projects/" + projectId + "/candidates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"triageCompanyId":"%s","fullName":"%s"%s}""".formatted(triageCompanyId, fullName, country)))
                .andExpect(status().isCreated());
    }

    private String companyNamed(String projectId, String companyName) {
        return db.queryForObject(
                "SELECT id FROM app_lm_project_triage_company WHERE project_id = ? AND company_name = ?",
                UUID.class, UUID.fromString(projectId), companyName).toString();
    }

    private void seed(String projectId, CompanyRow... rows) {
        UUID actor = db.queryForObject("SELECT id FROM app_lm_user WHERE email = ?", UUID.class, "alok@" + domain);
        writer.insertIgnoringHeld(UUID.fromString(projectId), actor, List.of(rows), TriageCompanySource.STRATEGY,
                TriageCompanyStatus.IN_UNIVERSE, null);
    }

    private static CompanyRow row(String apolloAccountId, String companyName, String country) {
        return new CompanyRow(apolloAccountId, companyName, "oil & energy", country, null,
                100, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, List.of(), List.of(), List.of(), List.of());
    }

    private static String linesUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/triage/lines";
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
                                {"customName":"Lines Client %s"}""".formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"Head of Retail"}""".formatted(clientId)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }
}
