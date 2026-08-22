package app.lightmove.api.strategy;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Saved searches: saving takes the filter the mandate has actually stored, a saved search is frozen
 * against later edits, names are unique within a mandate, and another mandate's search is invisible.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class StrategySearchIntegrationTest extends FlowTestSupport {

    @Test
    @DisplayName("a saved search captures the filter as it stands, not a payload from the client")
    void savingCapturesTheStoredFilter() throws Exception {
        String admin = adminOf("Search Capture Firm");
        String projectId = project(admin);
        putFilter(admin, projectId, """
                {"filter":{"industries":["oil & energy"],"marketSegments":["B2B"],
                           "countries":["Qatar"],"employeeBands":["1001-2000"],"revenueBands":[]
                           }}""");

        // The request body carries a name and nothing else — what is saved is what is on screen.
        mvc.perform(post(searchesUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"GCC utilities"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("GCC utilities"))
                .andExpect(jsonPath("$.filter.industries[0]").value("oil & energy"))
                .andExpect(jsonPath("$.filter.marketSegments[0]").value("B2B"))
                .andExpect(jsonPath("$.filter.countries[0]").value("Qatar"))
                .andExpect(jsonPath("$.filter.employeeBands[0]").value("1001-2000"));
    }

    @Test
    @DisplayName("a saved search is frozen — later edits to the filter do not follow it")
    void savedSearchIsFrozen() throws Exception {
        String admin = adminOf("Search Frozen Firm");
        String projectId = project(admin);
        putFilter(admin, projectId, """
                {"filter":{"industries":["oil & energy"],"marketSegments":[],"countries":[],
                           "employeeBands":[],"revenueBands":[]}}""");
        save(admin, projectId, "Energy");

        putFilter(admin, projectId, """
                {"filter":{"industries":["retail"],"marketSegments":[],"countries":[],
                           "employeeBands":[],"revenueBands":[]}}""");

        // Holding a reference rather than a copy is exactly what a saved search exists not to do.
        mvc.perform(get(strategyUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.filter.industries[0]").value("retail"))
                .andExpect(jsonPath("$.searches[0].filter.industries[0]").value("oil & energy"));
    }

    @Test
    @DisplayName("saved searches arrive with the strategy, by name")
    void searchesArriveWithTheStrategy() throws Exception {
        String admin = adminOf("Search Listing Firm");
        String projectId = project(admin);
        save(admin, projectId, "Zeta");
        save(admin, projectId, "Alpha");

        mvc.perform(get(strategyUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.searches.length()").value(2))
                .andExpect(jsonPath("$.searches[0].name").value("Alpha"))
                .andExpect(jsonPath("$.searches[1].name").value("Zeta"));
    }

    @Test
    @DisplayName("a name that differs only by case collides, so one dropdown cannot hold both")
    void namesAreUniqueCaseInsensitively() throws Exception {
        String admin = adminOf("Search Name Firm");
        String projectId = project(admin);
        save(admin, projectId, "GCC utilities");

        MvcResult result = mvc.perform(post(searchesUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"gcc UTILITIES"}"""))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("two mandates may each hold a search of the same name")
    void namesCollideOnlyWithinAMandate() throws Exception {
        String admin = adminOf("Search Scope Firm");
        String first = project(admin);
        String second = project(admin);

        save(admin, first, "Shared name");
        mvc.perform(post(searchesUrl(second))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Shared name"}"""))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("a search renames and deletes")
    void renameAndDelete() throws Exception {
        String admin = adminOf("Search Edit Firm");
        String projectId = project(admin);
        String searchId = save(admin, projectId, "Old name");

        mvc.perform(patch(searchesUrl(projectId) + "/" + searchId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"New name"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New name"));

        mvc.perform(delete(searchesUrl(projectId) + "/" + searchId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        mvc.perform(get(strategyUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.searches.length()").value(0));
    }

    @Test
    @DisplayName("a blank name is rejected")
    void blankNameRejected() throws Exception {
        String admin = adminOf("Search Blank Firm");
        String projectId = project(admin);

        MvcResult result = mvc.perform(post(searchesUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"   "}"""))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(result)).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("another mandate's search cannot be renamed or deleted through this one")
    void anotherMandatesSearchIsNotFound() throws Exception {
        String admin = adminOf("Search Isolation Firm");
        String owner = project(admin);
        String other = project(admin);
        String searchId = save(admin, owner, "Owned");

        // Scoped by (id, projectId), so naming the wrong mandate is a 404 rather than a cross-edit.
        mvc.perform(delete(searchesUrl(other) + "/" + searchId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
    }

    private String save(String token, String projectId, String name) throws Exception {
        return body(mvc.perform(post(searchesUrl(projectId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s"}""".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private void putFilter(String token, String projectId, String bodyJson) throws Exception {
        mvc.perform(put(strategyUrl(projectId) + "/filter")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyJson))
                .andExpect(status().isOk());
    }

    private static String strategyUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/strategy";
    }

    private static String searchesUrl(String projectId) {
        return strategyUrl(projectId) + "/searches";
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
                                {"customName":"Search Client %s"}""".formatted(java.util.UUID.randomUUID())))
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
