package app.uncava.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.uncava.api.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

/**
 * An executive hidden from the client (V132) is absent from every read a client seat can reach — the list,
 * the drawer, the photo, the Companies grid's filters and sort, the talent map, the report, the export, the
 * projects list's counts and a personal API key — and a client seat sees exactly what it saw before while
 * everyone is visible. Staff, and a workspace API key, still read them everywhere.
 */
@IntegrationTest
class ClientVisibilityTest extends PipelineFlowSupport {

    private String projectId;
    private String shown;
    private String hidden;
    private String client;
    private String researcher;

    @BeforeEach
    void aPositionWithOneHiddenExecutive() throws Exception {
        firm("Client Visibility Firm");
        projectId = mandate("Chief Financial Officer");
        String company = capture(projectId, "ACWA Power");
        shown = add(projectId, """
                {"triageCompanyId":"%s","fullName":"Shown Person","locationCountry":"United Arab Emirates"}"""
                .formatted(company)).get("id").asText();
        hidden = add(projectId, """
                {"triageCompanyId":"%s","fullName":"Hidden Person","locationCountry":"United Arab Emirates"}"""
                .formatted(company)).get("id").asText();
        client = clientOn(projectId);
        researcher = researcherOn(projectId, "sara");
    }

    @Test
    @DisplayName("while everyone is visible, a client seat reads every executive, as it always did")
    void everyoneVisibleByDefault() throws Exception {
        assertThat(read(client, candidatesUrl(projectId))).contains("Shown Person", "Hidden Person");
        assertThat(read(client, "/api/v1/projects/" + projectId + "/talent-map")).contains("Hidden Person");
    }

    @Test
    @DisplayName("a hidden executive is absent from every read a client seat reaches, and a 404 by id")
    void aHiddenExecutiveIsAbsentForAClient() throws Exception {
        write(admin, "PUT", candidatesUrl(projectId) + "/" + hidden + "/client-visibility", """
                {"clientVisible":false}""");

        assertThat(read(client, candidatesUrl(projectId))).contains("Shown Person").doesNotContain("Hidden Person");
        assertThat(mvc.perform(get(candidatesUrl(projectId) + "/" + hidden).header("Authorization", "Bearer " + client))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mvc.perform(get(candidatesUrl(projectId) + "/" + hidden + "/photo")
                .header("Authorization", "Bearer " + client)).andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(read(client, "/api/v1/projects/" + projectId + "/talent-map")).contains("Shown Person")
                .doesNotContain("Hidden Person");
        assertThat(read(client, "/api/v1/projects/" + projectId + "/report"))
                .contains("\"executivesMapped\":1");
        assertThat(read(client, "/api/v1/projects/" + projectId + "/export/companies")).contains("Shown Person")
                .doesNotContain("Hidden Person");
    }

    @Test
    @DisplayName("staff and an API key still read a hidden executive everywhere")
    void staffStillSeeThem() throws Exception {
        write(admin, "PUT", candidatesUrl(projectId) + "/" + hidden + "/client-visibility", """
                {"clientVisible":false}""");

        for (String staff : new String[] {admin, researcher}) {
            assertThat(read(staff, candidatesUrl(projectId))).contains("Hidden Person");
            assertThat(read(staff, candidatesUrl(projectId) + "/" + hidden)).contains("\"clientVisible\":false");
            assertThat(read(staff, "/api/v1/projects/" + projectId + "/talent-map")).contains("Hidden Person");
            assertThat(read(staff, "/api/v1/projects/" + projectId + "/report")).contains("\"executivesMapped\":2");
            assertThat(read(staff, "/api/v1/projects/" + projectId + "/export/companies")).contains("Hidden Person");
        }
        String key = body(mvc.perform(post("/api/v1/workspace/api-keys").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Universe","scopes":["companies:read","candidates:read"]}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("secret").asText();
        assertThat(read(key, "/api/v1/public/projects/" + projectId + "/universe")).contains("Hidden Person");
    }

    @Test
    @DisplayName("a hidden executive matches neither Companies filter and moves nobody's place in its status sort")
    void theCompaniesGridIgnoresAHiddenExecutiveForAClient() throws Exception {
        moveTo(projectId, shown, "interested");
        String masdar = capture(projectId, "Masdar");
        add(projectId, """
                {"triageCompanyId":"%s","fullName":"Other Person","status":"contacted"}""".formatted(masdar));
        hide(hidden);

        assertThat(companies(admin, "executiveQuery=hidden")).containsExactly("ACWA Power");
        assertThat(companies(client, "executiveQuery=hidden")).isEmpty();
        assertThat(companies(admin, "executiveStatuses=identified")).containsExactly("ACWA Power");
        assertThat(companies(client, "executiveStatuses=identified")).isEmpty();

        String byStatus = "sort=executiveStatus&direction=asc";
        assertThat(companies(researcher, byStatus)).as("staff rank ACWA by its hidden Identified executive")
                .containsExactly("ACWA Power", "Masdar");
        assertThat(companies(client, byStatus)).as("a client seat ranks it by the Interested one it can see")
                .containsExactly("Masdar", "ACWA Power");
        assertThat(companies(client, byStatus + "&executiveQuery=hidden")).isEmpty();
        assertThat(companies(client, byStatus + "&executiveStatuses=identified")).isEmpty();
        assertThat(companies(admin, byStatus + "&executiveStatuses=identified")).containsExactly("ACWA Power");
    }

    @Test
    @DisplayName("the projects list counts only the executives a client seat can see")
    void theProjectsListCountsWhatTheReaderSees() throws Exception {
        moveTo(projectId, hidden, "engaged");
        String masdar = capture(projectId, "Masdar");
        String alone = add(projectId, """
                {"triageCompanyId":"%s","fullName":"Lone Person"}""".formatted(masdar)).get("id").asText();
        hide(hidden);
        hide(alone);

        for (String staff : new String[] {admin, researcher}) {
            JsonNode whole = projectRow(staff);
            assertThat(whole.get("candidates").asLong()).isEqualTo(3);
            assertThat(whole.get("mappedCandidates").asLong()).isEqualTo(3);
            assertThat(whole.get("engagedCandidates").asLong()).isEqualTo(1);
            assertThat(whole.get("mappedCompanies").asLong()).isEqualTo(2);
        }
        JsonNode seen = projectRow(client);
        assertThat(seen.get("candidates").asLong()).isEqualTo(1);
        assertThat(seen.get("mappedCandidates").asLong()).isEqualTo(1);
        assertThat(seen.get("engagedCandidates").asLong()).isZero();
        assertThat(seen.get("mappedCompanies").asLong()).isEqualTo(1);
        assertThat(seen.get("stage").asText()).as("nobody a client sees was approached")
                .isNotEqualTo(projectRow(admin).get("stage").asText());
    }

    @Test
    @DisplayName("a colleague holding only the client seat reads as a client, through their session and their own API key")
    void aPersonalKeyReadsAsItsOwnersSeat() throws Exception {
        String colleague = member("omar");
        mvc.perform(post("/api/v1/projects/" + projectId + "/representatives/invitations")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Omar","position":"Chair","email":"omar@%s"}""".formatted(domain)))
                .andExpect(status().isOk());
        hide(hidden);
        String key = body(mvc.perform(post("/api/v1/workspace/api-keys")
                        .header("Authorization", "Bearer " + colleague)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Omar's sheet","scopes":["projects:read","companies:read","candidates:read"]}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("secret").asText();
        String position = "/api/v1/public/projects/" + projectId;

        assertThat(read(colleague, candidatesUrl(projectId))).contains("Shown Person").doesNotContain("Hidden Person");
        assertThat(projectRow(colleague).get("candidates").asLong()).isEqualTo(1);
        assertThat(read(key, position + "/candidates")).contains("Shown Person").doesNotContain("Hidden Person");
        assertThat(mvc.perform(get(position + "/candidates/" + hidden).header("Authorization", "Bearer " + key))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(read(key, position + "/universe")).contains("Shown Person").doesNotContain("Hidden Person");
        assertThat(body(mvc.perform(get(position).header("Authorization", "Bearer " + key)).andReturn())
                .get("candidates").asLong()).isEqualTo(1);
        assertThat(body(mvc.perform(get(position + "/summary").header("Authorization", "Bearer " + key)).andReturn())
                .at("/executives/identified").asLong()).isEqualTo(1);
    }

    private void hide(String candidateId) throws Exception {
        assertThat(write(admin, "PUT", candidatesUrl(projectId) + "/" + candidateId + "/client-visibility", """
                {"clientVisible":false}""").getResponse().getStatus()).isEqualTo(200);
    }

    private List<String> companies(String token, String query) throws Exception {
        List<String> names = new ArrayList<>();
        body(mvc.perform(get("/api/v1/projects/" + projectId + "/triage?" + query)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()).get("companies").forEach(company -> names.add(company.get("companyName").asText()));
        return names;
    }

    private JsonNode projectRow(String token) throws Exception {
        for (JsonNode row : body(mvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn())) {
            if (row.get("id").asText().equals(projectId)) {
                return row;
            }
        }
        throw new AssertionError("no project " + projectId);
    }

    private String read(String token, String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
