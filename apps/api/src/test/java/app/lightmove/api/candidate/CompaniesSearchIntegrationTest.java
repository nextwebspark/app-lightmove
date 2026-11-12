package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * The Companies search box means one thing everywhere it is read — the triage list, the people beside it, the
 * talent map, the export and the Pipeline: a company on its own facts keeps everyone at it, an executive on theirs
 * keeps their company, and notes and tags count only for a reader holding {@code WORK_EXECUTE}.
 */
@IntegrationTest
class CompaniesSearchIntegrationTest extends PipelineFlowSupport {

    private String projectId;
    private String alshaya;
    private String masdar;
    private String quiet;
    private String khaled;
    private String rana;
    private String omar;

    private void universe(String firmName) throws Exception {
        firm(firmName);
        projectId = mandate("Chief Financial Officer");
        alshaya = company(projectId, "Alshaya", "Kuwait City", "Kuwait", "Retail");
        masdar = company(projectId, "Masdar", "Abu Dhabi", "United Arab Emirates", "Renewables & Environment");
        quiet = company(projectId, "Quiet Holdings", "Doha", "Qatar", "Investment Management");
        khaled = executive(projectId, alshaya, "Khaled Al Beirouty", "Group CFO",
                "https://www.linkedin.com/in/khaled-al-beirouty");
        rana = executive(projectId, alshaya, "Rana Haddad", "Treasurer", null);
        omar = executive(projectId, masdar, "Omar Farouk", "Finance Director", null);
    }

    @Test
    @DisplayName("a company's own facts keep all its executives; an executive's keep their company and them alone")
    void companyAndExecutiveFacts() throws Exception {
        universe("Search Facts Firm");

        assertThat(companies(admin, "retail")).containsExactly("Alshaya");
        assertThat(people(admin, alshaya, "retail")).containsExactlyInAnyOrder("Khaled Al Beirouty", "Rana Haddad");
        assertThat(companies(admin, "abu dhabi")).containsExactly("Masdar");

        assertThat(companies(admin, "treasurer")).containsExactly("Alshaya");
        assertThat(people(admin, alshaya, "treasurer")).containsExactly("Rana Haddad");
        assertThat(companies(admin, "FAROUK")).containsExactly("Masdar");
        assertThat(people(admin, masdar, "farouk")).containsExactly("Omar Farouk");

        assertThat(companies(admin, "nothing like it")).isEmpty();
        assertThat(companies(admin, "%")).isEmpty();
        assertThat(companies(admin, "  ")).containsExactlyInAnyOrder("Alshaya", "Masdar", "Quiet Holdings");
        assertThat(triage(admin, "retail").get("totalCount").asLong()).isEqualTo(1);

        assertThat(rankedByStatus("treasurer")).containsExactly("Alshaya");
        assertThat(rankedByStatus(null)).containsExactlyInAnyOrder("Alshaya", "Masdar", "Quiet Holdings");
    }

    private List<String> rankedByStatus(String search) throws Exception {
        var request = get("/api/v1/projects/" + projectId + "/triage").param("sort", "executiveStatus");
        if (search != null) {
            request = request.param("search", search);
        }
        JsonNode page = body(mvc.perform(request.header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
        List<String> names = new ArrayList<>();
        page.get("companies").forEach(company -> names.add(company.get("companyName").asText()));
        return names;
    }

    @Test
    @DisplayName("staff match a tag, this position's note, a person-level note and a company note; a client seat none")
    void staffContentIsStaffOnly() throws Exception {
        universe("Search Staff Firm");
        tag(personOf(omar), "Board-ready");
        note(projectId, khaled, "Speaks fluent Mandarin", false);
        note(projectId, rana, "Relocating to Riyadh next year", true);
        companyNote(projectId, quiet, "Family office near Zanzibar");
        String client = clientOn(projectId);

        assertThat(companies(admin, "board-ready")).containsExactly("Masdar");
        assertThat(people(admin, masdar, "board-ready")).containsExactly("Omar Farouk");
        assertThat(companies(admin, "mandarin")).containsExactly("Alshaya");
        assertThat(people(admin, alshaya, "mandarin")).containsExactly("Khaled Al Beirouty");
        assertThat(companies(admin, "riyadh")).containsExactly("Alshaya");
        assertThat(people(admin, alshaya, "riyadh")).containsExactly("Rana Haddad");
        assertThat(companies(admin, "zanzibar")).containsExactly("Quiet Holdings");

        for (String needle : List.of("board-ready", "mandarin", "riyadh", "zanzibar")) {
            assertThat(companies(client, needle)).as(needle).isEmpty();
            assertThat(people(client, alshaya, needle)).as(needle).isEmpty();
            assertThat(talentMapCompanies(client, needle)).as(needle).isEmpty();
        }
        assertThat(companies(client, "retail")).containsExactly("Alshaya");
    }

    @Test
    @DisplayName("another position's note and a deleted note never match")
    void otherPositionsAndDeletedNotesNeverMatch() throws Exception {
        universe("Search Viewpoint Firm");
        String otherId = mandate("Chief Operating Officer");
        String otherCompany = company(otherId, "Elsewhere Group", "Muscat", "Oman", "Logistics");
        String khaledThere = executive(otherId, otherCompany, "Khaled Al Beirouty", "Group CFO",
                "https://www.linkedin.com/in/khaled-al-beirouty");
        assertThat(personOf(khaledThere)).isEqualTo(personOf(khaled));
        note(otherId, khaledThere, "Golf partner of the chairman", false);
        String removed = note(projectId, omar, "Codeword zebra", false);
        mvc.perform(delete(candidatesUrl(projectId) + "/" + omar + "/notes/" + removed)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        assertThat(companies(admin, "golf")).isEmpty();
        assertThat(pipelineNames("golf")).isEmpty();
        assertThat(body(mvc.perform(get("/api/v1/projects/" + otherId + "/triage").param("search", "golf")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn()).at("/companies/0/companyName").asText())
                .isEqualTo("Elsewhere Group");
        assertThat(companies(admin, "zebra")).isEmpty();
    }

    @Test
    @DisplayName("the export and the talent map show exactly what the list does")
    void exportAndMapAgreeWithTheList() throws Exception {
        universe("Search Export Firm");
        note(projectId, khaled, "Speaks fluent Mandarin", false);

        String csv = read(admin, "/api/v1/projects/" + projectId + "/export/companies?search=retail");
        assertThat(csv).contains("Alshaya", "Khaled Al Beirouty", "Rana Haddad").doesNotContain("Masdar", "Omar");
        String narrowed = read(admin, "/api/v1/projects/" + projectId + "/export/companies?search=mandarin");
        assertThat(narrowed).contains("Alshaya", "Khaled Al Beirouty").doesNotContain("Rana Haddad", "Masdar");

        assertThat(talentMapCompanies(admin, "treasurer")).containsExactly("Alshaya");
        JsonNode map = talentMap(admin, "treasurer");
        List<String> mapped = new ArrayList<>();
        map.get("candidates").forEach(person -> mapped.add(person.get("fullName").asText()));
        assertThat(mapped).containsExactly("Rana Haddad");
    }

    @Test
    @DisplayName("the Pipeline lists every executive whatever their stage, and its search is the same rule")
    void pipelineSearchIsTheSameRule() throws Exception {
        universe("Search Pipeline Firm");
        note(projectId, khaled, "Speaks fluent Mandarin", false);
        write(admin, "PUT", candidatesUrl(projectId) + "/" + rana + "/triage-status", """
                {"status":"declined"}""");

        assertThat(pipelineNames(null)).containsExactlyInAnyOrder("Khaled Al Beirouty", "Rana Haddad", "Omar Farouk");
        long counted = 0;
        for (JsonNode stage : pipeline(null).get("stageCounts")) {
            counted += stage.get("count").asLong();
        }
        assertThat(counted).isEqualTo(3);
        assertThat(pipelineNames("mandarin")).containsExactly("Khaled Al Beirouty");
        assertThat(pipelineNames("retail")).containsExactlyInAnyOrder("Khaled Al Beirouty", "Rana Haddad");
        assertThat(pipelineNames("treasurer")).containsExactly("Rana Haddad");
        assertThat(pipelineNames("nothing like it")).isEmpty();
    }

    private String company(String project, String name, String city, String country, String industry)
            throws Exception {
        return body(write(admin, "POST", "/api/v1/projects/" + project + "/triage/capture", """
                {"companyName":"%s","companyCity":"%s","companyCountry":"%s","industry":"%s"}"""
                .formatted(name, city, country, industry))).get("id").asText();
    }

    private String executive(String project, String triageCompanyId, String fullName, String title,
                             String linkedinUrl) throws Exception {
        return add(project, """
                {"triageCompanyId":"%s","fullName":"%s","title":"%s"%s}""".formatted(triageCompanyId, fullName,
                title, linkedinUrl == null ? "" : ",\"linkedinUrl\":\"" + linkedinUrl + "\""))
                .get("id").asText();
    }

    private String personOf(String candidateId) throws Exception {
        return body(mvc.perform(get(candidatesUrl(projectIdOf(candidateId)) + "/" + candidateId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn()).get("personId").asText();
    }

    private String projectIdOf(String candidateId) {
        return db.queryForObject("select project_id::text from app_lm_project_candidate where id = ?::uuid",
                String.class, candidateId);
    }

    private void tag(String personId, String label) throws Exception {
        String tagId = body(write(admin, "POST", "/api/v1/candidate-tags", """
                {"label":"%s"}""".formatted(label))).get("id").asText();
        assertThat(write(admin, "PUT", "/api/v1/candidates/" + personId + "/tags/" + tagId, "{}")
                .getResponse().getStatus()).isLessThan(300);
    }

    private String note(String project, String candidateId, String text, boolean allPositions) throws Exception {
        return body(write(admin, "POST", candidatesUrl(project) + "/" + candidateId + "/notes", """
                {"kind":"general","body":"%s","allPositions":%s}""".formatted(text, allPositions)))
                .get("id").asText();
    }

    private void companyNote(String project, String triageCompanyId, String text) throws Exception {
        assertThat(write(admin, "POST", "/api/v1/projects/" + project + "/triage/" + triageCompanyId + "/notes", """
                {"kind":"general","body":"%s"}""".formatted(text)).getResponse().getStatus()).isEqualTo(201);
    }

    private JsonNode triage(String token, String search) throws Exception {
        return body(mvc.perform(get("/api/v1/projects/" + projectId + "/triage").param("search", search)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
    }

    private List<String> companies(String token, String search) throws Exception {
        List<String> names = new ArrayList<>();
        triage(token, search).get("companies").forEach(company -> names.add(company.get("companyName").asText()));
        return names;
    }

    private List<String> people(String token, String triageCompanyId, String search) throws Exception {
        JsonNode page = body(mvc.perform(get(candidatesUrl(projectId))
                        .param("triageCompanyId", triageCompanyId).param("triageStatus", "inUniverse")
                        .param("search", search)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
        List<String> names = new ArrayList<>();
        page.get("candidates").forEach(candidate -> names.add(candidate.get("fullName").asText()));
        return names;
    }

    private JsonNode talentMap(String token, String search) throws Exception {
        return body(mvc.perform(get("/api/v1/projects/" + projectId + "/talent-map").param("search", search)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
    }

    private List<String> talentMapCompanies(String token, String search) throws Exception {
        List<String> names = new ArrayList<>();
        talentMap(token, search).get("companies").forEach(company -> names.add(company.get("companyName").asText()));
        return names;
    }

    private JsonNode pipeline(String query) throws Exception {
        var request = get("/api/v1/projects/" + projectId + "/pipeline");
        if (query != null) {
            request = request.param("q", query);
        }
        return body(mvc.perform(request.header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
    }

    private List<String> pipelineNames(String query) throws Exception {
        List<String> names = new ArrayList<>();
        pipeline(query).get("rows").forEach(row -> names.add(row.get("fullName").asText()));
        return names;
    }

    private String read(String token, String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
