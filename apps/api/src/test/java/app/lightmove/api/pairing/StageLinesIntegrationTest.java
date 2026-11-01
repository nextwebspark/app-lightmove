package app.lightmove.api.pairing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.ApolloUniverse;
import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.TestWorkspaceScopes;
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

    @Autowired @FixtureJdbc JdbcTemplate db;
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

    @Test
    @DisplayName("an executive at no company of the mandate sorts among the rest, and a company-name filter leaves them out")
    void unassignedExecutiveSortsAmongTheRest() throws Exception {
        String admin = adminOf("Lines Unassigned Firm");
        String projectId = project(admin);
        seed(projectId, row("a1", "Alpha Industrial", "Kuwait"));
        mapExecutive(admin, projectId, companyNamed(projectId, "Alpha Industrial"), "Zaid Haddad", null);
        mvc.perform(post("/api/v1/projects/" + projectId + "/candidates")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Amal Nasser","employerName":"Outside Holdings"}"""))
                .andExpect(status().isCreated());

        JsonNode page = lines(admin, projectId, "executive", "asc");
        assertThat(executivesOf(page)).containsExactly("Amal Nasser", "Zaid Haddad");
        assertThat(page.at("/lines/0/company").isNull()).isTrue();
        assertThat(page.at("/lines/0/candidate/companyName").asText()).isEqualTo("Outside Holdings");

        JsonNode filtered = body(mvc.perform(get(linesUrl(projectId)).param("q", "alpha")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(executivesOf(filtered)).containsExactly("Zaid Haddad");
    }

    @Test
    @DisplayName("the executive filters narrow lines per person, so a kept company draws only its matching colleague")
    void executiveFiltersNarrowPerPerson() throws Exception {
        String admin = adminOf("Lines Filter Firm");
        String projectId = project(admin);
        seed(projectId, row("a1", "Alpha Industrial", "Bahrain"), row("a2", "Beta Trading", "Bahrain"));
        String alpha = companyNamed(projectId, "Alpha Industrial");
        mapExecutive(admin, projectId, alpha, "Yasmin Saleh", null, "interested");
        mapExecutive(admin, projectId, alpha, "Karim Aziz", null, "identified");

        JsonNode byName = body(mvc.perform(get(linesUrl(projectId)).param("executiveQuery", "yasmin")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(executivesOf(byName)).containsExactly("Yasmin Saleh");
        assertThat(byName.get("totalCount").asInt()).isEqualTo(1);

        JsonNode byStatus = body(mvc.perform(get(linesUrl(projectId)).param("executiveStatuses", "identified")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(executivesOf(byStatus)).containsExactly("Karim Aziz");
        assertThat(byStatus.at("/counts/inUniverse").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("Status sorts in the pipeline's order, not by the status's name, with the empty slot last")
    void statusSortsInPipelineOrder() throws Exception {
        String admin = adminOf("Lines Status Firm");
        String projectId = project(admin);
        seed(projectId, row("a1", "Alpha Industrial", "Qatar"), row("a2", "Beta Trading", "Qatar"),
                row("a3", "Gamma Group", "Qatar"));
        mapExecutive(admin, projectId, companyNamed(projectId, "Alpha Industrial"), "Further Along", null, "interested");
        mapExecutive(admin, projectId, companyNamed(projectId, "Beta Trading"), "Just Found", null, "identified");

        assertThat(executivesOf(lines(admin, projectId, "executiveStatus", "asc")))
                .containsExactly("Just Found", "Further Along", "—");
        assertThat(executivesOf(lines(admin, projectId, "executiveStatus", "desc")))
                .containsExactly("Further Along", "Just Found", "—");
    }

    @Test
    @DisplayName("a mandate's own number column sorts by value, not as text, with a company holding none last")
    void customNumberColumnSortsByValue() throws Exception {
        String admin = adminOf("Lines Custom Firm");
        String projectId = project(admin);
        seed(projectId, row("a1", "Alpha Industrial", "Oman"), row("a2", "Beta Trading", "Oman"),
                row("a3", "Gamma Group", "Oman"));
        mvc.perform(post("/api/v1/projects/" + projectId + "/custom-columns")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"target":"company","label":"Sites","dataType":"number"}"""))
                .andExpect(status().isCreated());
        setSites(admin, projectId, "Alpha Industrial", "10");
        setSites(admin, projectId, "Beta Trading", "9");

        assertThat(companiesOf(lines(admin, projectId, "custom:company:sites", "asc")))
                .containsExactly("Beta Trading", "Alpha Industrial", "Gamma Group");
        assertThat(companiesOf(lines(admin, projectId, "custom:company:sites", "desc")))
                .containsExactly("Alpha Industrial", "Beta Trading", "Gamma Group");
    }

    @Test
    @DisplayName("lines page in sorted order, and the count is of lines rather than companies")
    void pagesFollowTheOrder() throws Exception {
        String admin = adminOf("Lines Paging Firm");
        String projectId = project(admin);
        seed(projectId, row("a1", "Alpha Industrial", "Kuwait"), row("a2", "Beta Trading", "Kuwait"));
        String alpha = companyNamed(projectId, "Alpha Industrial");
        mapExecutive(admin, projectId, alpha, "Chadi Maalouf", null);
        mapExecutive(admin, projectId, alpha, "Amira Fawzi", null);

        JsonNode second = body(mvc.perform(get(linesUrl(projectId)).param("sort", "executive").param("direction", "asc")
                        .param("page", "1").param("size", "2")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(second.get("totalCount").asInt()).isEqualTo(3);
        assertThat(executivesOf(second)).containsExactly("—");
        assertThat(second.at("/lines/0/company/companyName").asText()).isEqualTo("Beta Trading");
    }

    @Test
    @DisplayName("a page past the end reads empty, however far past, never a failure")
    void pageFarPastTheEndReadsEmpty() throws Exception {
        String admin = adminOf("Lines Far Page Firm");
        String projectId = project(admin);
        seed(projectId, row("a1", "Alpha Industrial", "Kuwait"));

        JsonNode farPast = body(mvc.perform(get(linesUrl(projectId)).param("page", "100000000").param("size", "25")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(farPast.get("lines")).isEmpty();
        assertThat(farPast.get("totalCount").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("a client seat reads the lines without the firm's notes or adders, and may not sort by either")
    void clientSeatReadsWithoutStaffFacts() throws Exception {
        String admin = adminOf("Lines Client Firm");
        String projectId = project(admin);
        seed(projectId, row("a1", "Alpha Industrial", "Oman"));
        mapExecutive(admin, projectId, companyNamed(projectId, "Alpha Industrial"), "Huda Rashid", null);
        String client = clientOn(admin, projectId);

        JsonNode page = lines(client, projectId, "executive", "asc");
        assertThat(page.at("/lines/0/company/companyName").asText()).isEqualTo("Alpha Industrial");
        assertThat(page.at("/lines/0/company/noteCount").isNull()).isTrue();
        assertThat(page.at("/lines/0/company/addedByName").isNull()).isTrue();
        assertThat(page.get("addedBy").isNull()).isTrue();

        for (String staffOnly : List.of("note", "addedBy")) {
            mvc.perform(get(linesUrl(projectId)).param("sort", staffOnly)
                            .header("Authorization", "Bearer " + client))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("a member with no seat on the position cannot read its lines")
    void unseatedMemberRefused() throws Exception {
        String admin = adminOf("Lines Seat Firm");
        String projectId = project(admin);
        inviteAndAccept(admin, "Sara Al-Mansour", "sara@" + domain, "MEMBER");

        mvc.perform(get(linesUrl(projectId)).header("Authorization", "Bearer " + login("sara@" + domain)))
                .andExpect(status().isForbidden());
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
        mapExecutive(token, projectId, triageCompanyId, fullName, locationCountry, null);
    }

    private void mapExecutive(String token, String projectId, String triageCompanyId, String fullName,
                              String locationCountry, String status) throws Exception {
        String country = locationCountry == null ? "" : ",\"locationCountry\":\"%s\"".formatted(locationCountry);
        String pipelineStatus = status == null ? "" : ",\"status\":\"%s\"".formatted(status);
        mvc.perform(post("/api/v1/projects/" + projectId + "/candidates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"triageCompanyId":"%s","fullName":"%s"%s%s}"""
                                .formatted(triageCompanyId, fullName, country, pipelineStatus)))
                .andExpect(status().isCreated());
    }

    private void setSites(String token, String projectId, String companyName, String sites) throws Exception {
        mvc.perform(patch("/api/v1/projects/" + projectId + "/triage/" + companyNamed(projectId, companyName)
                                + "/custom-fields")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customFields":{"sites":"%s"}}""".formatted(sites)))
                .andExpect(status().isOk());
    }

    private String clientOn(String leadToken, String projectId) throws Exception {
        String clientEmail = "client@lines-" + domain;
        mvc.perform(post("/api/v1/projects/" + projectId + "/representatives/invitations")
                        .header("Authorization", "Bearer " + leadToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"A Client","position":"Chair","email":"%s"}""".formatted(clientEmail)))
                .andExpect(status().isOk());
        return body(mvc.perform(post("/api/v1/onboarding/accept-invitation-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","fullName":"A Client","password":"%s"}"""
                                .formatted(email.latestTokenFor(clientEmail), PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn()).get("accessToken").asText();
    }

    private String companyNamed(String projectId, String companyName) {
        return db.queryForObject(
                "SELECT id FROM app_lm_project_triage_company WHERE project_id = ? AND company_name = ?",
                UUID.class, UUID.fromString(projectId), companyName).toString();
    }

    private void seed(String projectId, CompanyRow... rows) {
        UUID actor = db.queryForObject("SELECT id FROM app_lm_user WHERE email = ?", UUID.class, "alok@" + domain);
        TestWorkspaceScopes.inProjectsWorkspace(db, UUID.fromString(projectId), () -> writer.insertIgnoringHeld(
                UUID.fromString(projectId), actor, List.of(rows), TriageCompanySource.STRATEGY,
                TriageCompanyStatus.IN_UNIVERSE, null));
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
