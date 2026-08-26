package app.lightmove.api.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
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
 *
 * <p>Also the two tiers. A SHARED search is the mandate's and any PROJECT_EDIT seat may rework it; a
 * PRIVATE one answers only to its author, and to everyone else it does not exist — which is why the
 * refusals below are 404 rather than 403.
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
                {"filter":{"industries":["oil & energy"],"keywords":[],"marketSegments":["B2B"],
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
                {"filter":{"industries":["oil & energy"],"keywords":[],"marketSegments":[],"countries":[],
                           "employeeBands":[],"revenueBands":[]}}""");
        save(admin, projectId, "Energy");

        putFilter(admin, projectId, """
                {"filter":{"industries":["retail"],"keywords":[],"marketSegments":[],"countries":[],
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
    @DisplayName("a name that differs only by case collides, and says which name is taken")
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
        // Not the generic CONFLICT: retrying will never fix a name that is already taken, so the
        // dropdown has to be able to say what is wrong with it.
        assertThat(codeOf(result)).isEqualTo("STRATEGY_SEARCH_NAME_TAKEN");
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

    @Test
    @DisplayName("a private search reaches its author and nobody else")
    void privateSearchIsInvisibleToTheTeam() throws Exception {
        String admin = adminOf("Search Private Firm");
        String projectId = project(admin);
        String sara = teammate(admin, projectId);

        save(admin, projectId, "My scratch", "PRIVATE");
        save(admin, projectId, "Team scope", "SHARED");

        mvc.perform(get(strategyUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.searches.length()").value(2));

        // The exclusion happens in the query, so no later code path can hold a private row and then
        // forget whose it was.
        mvc.perform(get(strategyUrl(projectId)).header("Authorization", "Bearer " + sara))
                .andExpect(jsonPath("$.searches.length()").value(1))
                .andExpect(jsonPath("$.searches[0].name").value("Team scope"));
    }

    @Test
    @DisplayName("a search says who saved it, and which tier it is in")
    void searchCarriesItsAuthor() throws Exception {
        String admin = adminOf("Search Author Firm");
        String projectId = project(admin);
        String searchId = save(admin, projectId, "GCC utilities");

        String createdAt = body(mvc.perform(get(strategyUrl(projectId))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.searches[0].createdByName").value("Alok Kumar"))
                .andExpect(jsonPath("$.searches[0].visibility").value("SHARED"))
                .andReturn()).get("searches").get(0).get("createdAt").asText();

        // updatedAt is written by @UpdateTimestamp at flush, so an edit that builds its response from
        // the still-dirty entity answers with the timestamp from before the edit it is reporting.
        mvc.perform(patch(searchesUrl(projectId) + "/" + searchId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"GCC utilities II"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedAt").value(not(createdAt)));
    }

    @Test
    @DisplayName("two people may each keep a private search of the same name")
    void privateNamesDoNotCollideAcrossPeople() throws Exception {
        String admin = adminOf("Search Private Name Firm");
        String projectId = project(admin);
        String sara = teammate(admin, projectId);

        save(admin, projectId, "Scratch", "PRIVATE");

        // One project-wide index would 409 here — reporting the existence of a row Sara may not see,
        // which is the one thing the private tier is for.
        mvc.perform(post(searchesUrl(projectId))
                        .header("Authorization", "Bearer " + sara)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Scratch","visibility":"PRIVATE"}"""))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("someone else's private search cannot be renamed or deleted, and 404s rather than 403s")
    void privateSearchIsNotEditableByTheTeam() throws Exception {
        String admin = adminOf("Search Private Guard Firm");
        String projectId = project(admin);
        String sara = teammate(admin, projectId);
        String searchId = save(admin, projectId, "My scratch", "PRIVATE");

        mvc.perform(patch(searchesUrl(projectId) + "/" + searchId)
                        .header("Authorization", "Bearer " + sara)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Hijacked"}"""))
                .andExpect(status().isNotFound());

        mvc.perform(put(searchesUrl(projectId) + "/" + searchId + "/filter")
                        .header("Authorization", "Bearer " + sara))
                .andExpect(status().isNotFound());

        mvc.perform(delete(searchesUrl(projectId) + "/" + searchId)
                        .header("Authorization", "Bearer " + sara))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a shared search stays the mandate's — a teammate may rework it")
    void sharedSearchIsTheMandates() throws Exception {
        String admin = adminOf("Search Shared Edit Firm");
        String projectId = project(admin);
        String sara = teammate(admin, projectId);
        String searchId = save(admin, projectId, "Team scope");

        mvc.perform(patch(searchesUrl(projectId) + "/" + searchId)
                        .header("Authorization", "Bearer " + sara)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Team scope v2"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Team scope v2"));
    }

    @Test
    @DisplayName("only the author moves a search between tiers")
    void onlyTheAuthorChangesTheTier() throws Exception {
        String admin = adminOf("Search Tier Firm");
        String projectId = project(admin);
        String sara = teammate(admin, projectId);
        String searchId = save(admin, projectId, "Team scope");

        // Pulling a shared search private would take the mandate's work out of the mandate's hands.
        mvc.perform(patch(searchesUrl(projectId) + "/" + searchId)
                        .header("Authorization", "Bearer " + sara)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Team scope","visibility":"PRIVATE"}"""))
                .andExpect(status().isForbidden());

        mvc.perform(patch(searchesUrl(projectId) + "/" + searchId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Team scope","visibility":"PRIVATE"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("PRIVATE"));

        mvc.perform(get(strategyUrl(projectId)).header("Authorization", "Bearer " + sara))
                .andExpect(jsonPath("$.searches.length()").value(0));
    }

    @Test
    @DisplayName("a private search promotes into the shared list, and the team then sees it")
    void privateSearchPromotesToShared() throws Exception {
        String admin = adminOf("Search Promote Firm");
        String projectId = project(admin);
        String sara = teammate(admin, projectId);
        String searchId = save(admin, projectId, "My scratch", "PRIVATE");

        mvc.perform(patch(searchesUrl(projectId) + "/" + searchId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Team scope","visibility":"SHARED"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("SHARED"));

        mvc.perform(get(strategyUrl(projectId)).header("Authorization", "Bearer " + sara))
                .andExpect(jsonPath("$.searches.length()").value(1))
                .andExpect(jsonPath("$.searches[0].name").value("Team scope"));
    }

    @Test
    @DisplayName("promotion cannot smuggle a name the shared list already holds")
    void promotionRespectsTheSharedNamespace() throws Exception {
        String admin = adminOf("Search Promote Clash Firm");
        String projectId = project(admin);
        save(admin, projectId, "Team scope", "SHARED");
        String searchId = save(admin, projectId, "My scratch", "PRIVATE");

        // The two namespaces are separate only while the row stays in its own tier — crossing over
        // has to meet the index on the far side.
        MvcResult result = mvc.perform(patch(searchesUrl(projectId) + "/" + searchId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Team scope","visibility":"SHARED"}"""))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(codeOf(result)).isEqualTo("STRATEGY_SEARCH_NAME_TAKEN");

        // The whole edit rolls back, name included — the audit row for it is written only once the
        // write is durable, so a refused promotion leaves no trace of having happened.
        mvc.perform(get(strategyUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.searches[?(@.name == 'My scratch')].visibility")
                        .value("PRIVATE"));
    }

    @Test
    @DisplayName("a tier change on its own leaves the name alone")
    void tierChangeNeedsNoName() throws Exception {
        String admin = adminOf("Search Tier Only Firm");
        String projectId = project(admin);
        String searchId = save(admin, projectId, "Team scope");

        // Both fields are optional. Requiring a name here meant the toggle had to resend whatever its
        // client last cached, which on a shared search is how one person's rename reverts another's.
        mvc.perform(patch(searchesUrl(projectId) + "/" + searchId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"visibility":"PRIVATE"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.name").value("Team scope"));
    }

    @Test
    @DisplayName("a name that is present but blank is still refused")
    void blankNameOnPatchRejected() throws Exception {
        String admin = adminOf("Search Patch Blank Firm");
        String projectId = project(admin);
        String searchId = save(admin, projectId, "Team scope");

        // Absent means "leave it alone"; blank never does, and @NotBlank can no longer say so.
        MvcResult result = mvc.perform(patch(searchesUrl(projectId) + "/" + searchId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"   "}"""))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(result)).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("re-capturing takes the mandate's current filter and leaves the name alone")
    void overwriteTakesTheStoredFilter() throws Exception {
        String admin = adminOf("Search Overwrite Firm");
        String projectId = project(admin);
        putFilter(admin, projectId, """
                {"filter":{"industries":["oil & energy"],"keywords":[],"marketSegments":[],"countries":[],
                           "employeeBands":[],"revenueBands":[]}}""");
        String searchId = save(admin, projectId, "Energy");

        putFilter(admin, projectId, """
                {"filter":{"industries":["retail"],"keywords":[],"marketSegments":[],"countries":[],
                           "employeeBands":[],"revenueBands":[]}}""");

        // No body: the server reads what the mandate has already autosaved, exactly as saving does.
        mvc.perform(put(searchesUrl(projectId) + "/" + searchId + "/filter")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Energy"))
                .andExpect(jsonPath("$.filter.industries[0]").value("retail"));
    }

    private String save(String token, String projectId, String name) throws Exception {
        return save(token, projectId, name, "SHARED");
    }

    private String save(String token, String projectId, String name, String visibility)
            throws Exception {
        return body(mvc.perform(post(searchesUrl(projectId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","visibility":"%s"}""".formatted(name, visibility)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    /** Sara, invited into the workspace and seated LEAD on the mandate so she holds PROJECT_EDIT. */
    private String teammate(String admin, String projectId) throws Exception {
        String sara = "sara@" + domain;
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");
        mvc.perform(put("/api/v1/projects/" + projectId + "/members/" + memberIdOf(admin, sara))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"LEAD"}"""))
                .andExpect(status().isOk());
        return login(sara);
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
