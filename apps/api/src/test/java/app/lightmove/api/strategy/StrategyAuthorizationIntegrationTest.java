package app.lightmove.api.strategy;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

/**
 * Strategy's action matrix. Reading is WORK_VIEW, which every seated role holds; writing the filter,
 * the off-limits list and the saved searches is PROJECT_EDIT, which a RESEARCHER does not have.
 *
 * <p>The line that matters most is the unseated member: a mandate's scope is team content, so
 * belonging to the workspace is not enough to read it, and a member with no seat is shut out even
 * though they may browse the projects list.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class StrategyAuthorizationIntegrationTest extends FlowTestSupport {

    private static final String FILTER_BODY = """
            {"filter":{"industries":["retail"],"keywords":[],"marketSegments":[],"countries":[],
                       "employeeBands":[],"revenueBands":[]}}""";

    @Test
    @DisplayName("an unseated member cannot read the strategy")
    void unseatedMemberCannotRead() throws Exception {
        Fixture f = fixture("Strategy Unseated Firm");
        String sara = login(f.saraEmail);

        mvc.perform(get(strategyUrl(f.projectId)).header("Authorization", "Bearer " + sara))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an unseated member cannot read the filtered company list either")
    void unseatedMemberCannotReadCompanies() throws Exception {
        Fixture f = fixture("Strategy Unseated List Firm");
        String sara = login(f.saraEmail);

        // The list reveals the team's chosen scope as directly as the filter does.
        mvc.perform(get(companiesUrl(f.projectId)).header("Authorization", "Bearer " + sara))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a seated researcher reads the strategy but cannot write the filter")
    void researcherReadsButCannotWrite() throws Exception {
        Fixture f = fixture("Strategy Researcher Firm");
        seat(f.admin, f.projectId, f.saraId, "RESEARCHER");
        String sara = login(f.saraEmail);

        mvc.perform(get(strategyUrl(f.projectId)).header("Authorization", "Bearer " + sara))
                .andExpect(status().isOk());
        mvc.perform(put(filterUrl(f.projectId))
                        .header("Authorization", "Bearer " + sara)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FILTER_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a seated lead writes the filter")
    void leadWritesTheFilter() throws Exception {
        Fixture f = fixture("Strategy Lead Firm");
        seat(f.admin, f.projectId, f.saraId, "LEAD");
        String sara = login(f.saraEmail);

        mvc.perform(put(filterUrl(f.projectId))
                        .header("Authorization", "Bearer " + sara)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FILTER_BODY))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("the off-limits list shares the filter's write gate")
    void offLimitsFollowsTheSameGate() throws Exception {
        Fixture f = fixture("Strategy Off Limits Gate Firm");
        String body = """
                {"apolloAccountIds":[]}""";

        seat(f.admin, f.projectId, f.saraId, "RESEARCHER");
        mvc.perform(put(offLimitsUrl(f.projectId))
                        .header("Authorization", "Bearer " + login(f.saraEmail))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        seat(f.admin, f.projectId, f.saraId, "LEAD");
        mvc.perform(put(offLimitsUrl(f.projectId))
                        .header("Authorization", "Bearer " + login(f.saraEmail))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("saving a search is a write: a researcher may read one but not leave one behind")
    void savingASearchFollowsTheWriteGate() throws Exception {
        Fixture f = fixture("Strategy Search Gate Firm");
        String body = """
                {"name":"Energy"}""";

        seat(f.admin, f.projectId, f.saraId, "RESEARCHER");
        mvc.perform(post(searchesUrl(f.projectId))
                        .header("Authorization", "Bearer " + login(f.saraEmail))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        seat(f.admin, f.projectId, f.saraId, "LEAD");
        mvc.perform(post(searchesUrl(f.projectId))
                        .header("Authorization", "Bearer " + login(f.saraEmail))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("a workspace admin reads a mandate they hold no seat on")
    void adminBypassesTheSeat() throws Exception {
        Fixture f = fixture("Strategy Admin Bypass Firm");

        mvc.perform(get(strategyUrl(f.projectId)).header("Authorization", "Bearer " + f.admin))
                .andExpect(status().isOk());
    }

    // ── fixture ──────────────────────────────────────────────────────────────

    private static String strategyUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/strategy";
    }

    private static String filterUrl(String projectId) {
        return strategyUrl(projectId) + "/filter";
    }

    private static String offLimitsUrl(String projectId) {
        return strategyUrl(projectId) + "/off-limits";
    }

    private static String companiesUrl(String projectId) {
        return strategyUrl(projectId) + "/companies";
    }

    private static String searchesUrl(String projectId) {
        return strategyUrl(projectId) + "/searches";
    }

    private record Fixture(String admin, String projectId, String saraEmail, String saraId) {}

    private Fixture fixture(String firmName) throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), firmName);
        String admin = login(alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");

        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Matrix Client"}"""))
                .andReturn()).get("id").asText();
        String projectId = body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"Head of Retail"}
                                """.formatted(clientId)))
                .andReturn()).get("id").asText();

        return new Fixture(admin, projectId, sara, memberIdOf(admin, sara));
    }

    private void seat(String leadToken, String projectId, String memberId, String role)
            throws Exception {
        mvc.perform(put("/api/v1/projects/" + projectId + "/members/" + memberId)
                        .header("Authorization", "Bearer " + leadToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"%s"}""".formatted(role)))
                .andExpect(status().isOk());
    }
}
