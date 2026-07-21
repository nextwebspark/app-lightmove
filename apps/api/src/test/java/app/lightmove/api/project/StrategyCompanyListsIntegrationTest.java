package app.lightmove.api.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The strategy's company lists end to end: a PUT of bare (source, sourceId) keys resolves its
 * name/display snapshot from the universe server-side, a second PUT fully replaces, the lists stay
 * independent of each other, and the guards reject — a key the universe doesn't hold, a duplicate,
 * and the same company on both lists. A stored entry survives its company vanishing upstream.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class StrategyCompanyListsIntegrationTest extends FlowTestSupport {

    @Autowired JdbcTemplate db;

    @BeforeEach
    void freshUniverse() {
        db.execute("DELETE FROM app_lm_companies");
    }

    @Test
    @DisplayName("a fresh project has empty target and off-limits lists")
    void firstReadSeedsEmptyLists() throws Exception {
        String admin = adminOf("Lists Seed Firm");
        String projectId = project(admin);

        mvc.perform(get(strategyUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targets.length()").value(0))
                .andExpect(jsonPath("$.offLimits.length()").value(0));
    }

    @Test
    @DisplayName("the targets PUT resolves the snapshot server-side and round-trips in order")
    void targetsResolveSnapshotsAndRoundTrip() throws Exception {
        String admin = adminOf("Lists Snapshot Firm");
        String projectId = project(admin);
        company("acme", "Acme Retail", "acme.example", "Everything store", "https://logo/acme.png",
                "Dubai", "AE");
        company("globex", "Globex Energy", null, null, null, "Riyadh", "SA");

        mvc.perform(put(targetsUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companies":[{"source":"test","sourceId":"globex"},
                                              {"source":"test","sourceId":"acme"}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targets.length()").value(2))
                .andExpect(jsonPath("$.targets[0].name").value("Globex Energy"))
                .andExpect(jsonPath("$.targets[1].name").value("Acme Retail"))
                .andExpect(jsonPath("$.targets[1].domain").value("acme.example"))
                .andExpect(jsonPath("$.targets[1].slogan").value("Everything store"))
                .andExpect(jsonPath("$.targets[1].logo").value("https://logo/acme.png"))
                .andExpect(jsonPath("$.targets[1].hqCity").value("Dubai"))
                .andExpect(jsonPath("$.targets[1].hqCountry").value("AE"));

        mvc.perform(get(strategyUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.targets.length()").value(2))
                .andExpect(jsonPath("$.targets[0].sourceId").value("globex"))
                .andExpect(jsonPath("$.offLimits.length()").value(0));
    }

    @Test
    @DisplayName("a second PUT replaces the whole list, and the sibling list is untouched")
    void secondPutReplacesAndListsStayIndependent() throws Exception {
        String admin = adminOf("Lists Replace Firm");
        String projectId = project(admin);
        company("acme", "Acme Retail", null, null, null, null, null);
        company("globex", "Globex Energy", null, null, null, null, null);
        company("initech", "Initech Systems", null, null, null, null, null);

        putList(admin, offLimitsUrl(projectId), keys("initech"));
        putList(admin, targetsUrl(projectId), keys("acme"));
        putList(admin, targetsUrl(projectId), keys("globex"));

        mvc.perform(get(strategyUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.targets.length()").value(1))
                .andExpect(jsonPath("$.targets[0].name").value("Globex Energy"))
                // The off-limits list survives every targets write.
                .andExpect(jsonPath("$.offLimits.length()").value(1))
                .andExpect(jsonPath("$.offLimits[0].name").value("Initech Systems"));
    }

    @Test
    @DisplayName("a key the universe does not hold is rejected")
    void unknownCompanyRejected() throws Exception {
        String admin = adminOf("Lists Unknown Firm");
        String projectId = project(admin);

        MvcResult result = mvc.perform(put(targetsUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(keys("ghost")))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(result)).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("a duplicate company within one request is rejected")
    void duplicateWithinRequestRejected() throws Exception {
        String admin = adminOf("Lists Dup Firm");
        String projectId = project(admin);
        company("acme", "Acme Retail", null, null, null, null, null);

        MvcResult result = mvc.perform(put(targetsUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(keys("acme", "acme")))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(result)).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("a company on the other list is rejected — targeted and barred is a contradiction")
    void crossListConflictRejected() throws Exception {
        String admin = adminOf("Lists Conflict Firm");
        String projectId = project(admin);
        company("acme", "Acme Retail", null, null, null, null, null);

        putList(admin, targetsUrl(projectId), keys("acme"));

        MvcResult result = mvc.perform(put(offLimitsUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(keys("acme")))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(result)).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("a stored entry survives its company vanishing upstream — only new keys must resolve")
    void storedEntrySurvivesUpstreamVanish() throws Exception {
        String admin = adminOf("Lists Vanish Firm");
        String projectId = project(admin);
        company("acme", "Acme Retail", "acme.example", null, null, null, null);
        company("globex", "Globex Energy", null, null, null, null, null);

        putList(admin, targetsUrl(projectId), keys("acme", "globex"));
        db.execute("DELETE FROM app_lm_companies WHERE source_id = 'acme'");

        // Removing globex re-PUTs the vanished acme; its stored snapshot must carry it through.
        putList(admin, targetsUrl(projectId), keys("acme"));

        mvc.perform(get(strategyUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.targets.length()").value(1))
                .andExpect(jsonPath("$.targets[0].name").value("Acme Retail"))
                .andExpect(jsonPath("$.targets[0].domain").value("acme.example"));
    }

    @Test
    @DisplayName("another workspace's lists do not exist, even to a verified user")
    void crossTenantMasked() throws Exception {
        String admin = adminOf("Lists Masked Firm");
        String projectId = project(admin);
        company("acme", "Acme Retail", null, null, null, null, null);
        String outsider = verifiedUser("Out Sider", "out@other-" + domain);

        MvcResult masked = mvc.perform(put(targetsUrl(projectId))
                        .header("Authorization", "Bearer " + outsider)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(keys("acme")))
                .andReturn();
        assertThat(masked.getResponse().getStatus()).isEqualTo(404);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String strategyUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/strategy";
    }

    private static String targetsUrl(String projectId) {
        return strategyUrl(projectId) + "/targets";
    }

    private static String offLimitsUrl(String projectId) {
        return strategyUrl(projectId) + "/off-limits";
    }

    /** A request body of test-source keys: {"companies":[{"source":"test","sourceId":...}, …]}. */
    private static String keys(String... sourceIds) {
        StringBuilder body = new StringBuilder("{\"companies\":[");
        for (int i = 0; i < sourceIds.length; i++) {
            if (i > 0) {
                body.append(',');
            }
            body.append("{\"source\":\"test\",\"sourceId\":\"").append(sourceIds[i]).append("\"}");
        }
        return body.append("]}").toString();
    }

    private void putList(String token, String url, String bodyJson) throws Exception {
        mvc.perform(put(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyJson))
                .andExpect(status().isOk());
    }

    private void company(String sourceId, String name, String domain, String slogan, String logo,
                         String hqCity, String hqCountry) {
        db.update("""
                        INSERT INTO app_lm_companies
                            (source, source_id, name, domain, slogan, logo, hq_city, hq_country)
                        VALUES ('test', ?, ?, ?, ?, ?, ?, ?)""",
                sourceId, name, domain, slogan, logo, hqCity, hqCountry);
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
                                {"name":"Lists Client"}"""))
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
