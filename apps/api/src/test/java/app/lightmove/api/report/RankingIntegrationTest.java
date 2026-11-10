package app.lightmove.api.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/**
 * The Ranking chapter: every assessed executive of a position at the brief's weighting — the drawer's own totals —
 * and the one scenario the team keeps beside it, refused when stale or aimed at a brief that has moved, cleared when
 * it is the brief's weighting again, and never read or written by a client seat.
 */
@IntegrationTest
class RankingIntegrationTest extends FlowTestSupport {

    private static final String BRIEF = """
            {"technical":[{"name":"Treasury","weight":60},{"name":"Reporting","weight":40}],
             "behavioural":[{"name":"Leadership","weight":100}],"technicalShare":60}""";
    private static final String TREASURY = "TECHNICAL:treasury";
    private static final String REPORTING = "TECHNICAL:reporting";
    private static final String LEADERSHIP = "BEHAVIOURAL:leadership";

    @Autowired @FixtureJdbc JdbcTemplate db;

    private String admin;

    @Test
    @DisplayName("the assessed are ranked at the drawer's totals; an assessment with no current score is unassessed")
    void readsEveryAssessmentAtTheBriefsWeighting() throws Exception {
        firm("Ranking Read Firm");
        String cfo = mandate();
        competencies(cfo, BRIEF);
        String reviewed = add(cfo, "Fatima Al Mazrouei");
        String drafted = add(cfo, "Khalid Al Harbi");
        String reportOnly = add(cfo, "Hamad Al Suwaidi");
        add(cfo, "Noura Al Qasimi");

        assess(cfo, reviewed, true, """
                [{"panel":"technical","name":"Treasury","score":9,"evidence":"Ran group treasury"},
                 {"panel":"technical","name":"Reporting","score":7},
                 {"panel":"behavioural","name":"Leadership","score":8}]""");
        assess(cfo, drafted, false, """
                [{"panel":"technical","name":"Treasury","score":6},
                 {"panel":"behavioural","name":"Leadership","score":9}]""");
        assess(cfo, reportOnly, false, "[]");

        JsonNode ranking = read(admin, cfo);
        assertThat(ranking.get("baselineTechnicalShare").asInt()).isEqualTo(60);
        assertThat(ranking.get("criteria").findValuesAsString("key")).containsExactly(TREASURY, REPORTING, LEADERSHIP);
        assertThat(ranking.get("mappedCount").asLong()).isEqualTo(4);
        assertThat(ranking.get("notAssessedCount").asLong()).isEqualTo(2);
        assertThat(ranking.get("truncated").asBoolean()).isFalse();
        assertThat(ranking.get("scenario").isNull()).isTrue();

        JsonNode candidates = ranking.get("candidates");
        assertThat(candidates.findValuesAsString("fullName")).containsExactly("Fatima Al Mazrouei", "Khalid Al Harbi");
        for (JsonNode candidate : candidates) {
            String id = candidate.get("candidateId").asText();
            assertThat(candidate.get("assessment").get("baselineOverall").asDouble())
                    .isEqualTo(assessment(cfo, id).get("overall").asDouble());
        }
        JsonNode first = candidates.get(0).get("assessment");
        assertThat(first.get("status").asText()).isEqualTo("reviewed");
        assertThat(first.get("scores").get(0).get("hasEvidence").asBoolean()).isTrue();
        JsonNode second = candidates.get(1).get("assessment");
        assertThat(second.get("status").asText()).isEqualTo("draft");
        assertThat(second.get("scoredCount").asInt()).isEqualTo(2);
        assertThat(second.get("scores").get(1).get("key").asText()).isEqualTo(REPORTING);
        assertThat(second.get("scores").get(1).get("score").isNull()).isTrue();
    }

    @Test
    @DisplayName("a scenario is kept, refused when stale or off 100, and cleared when it is the brief's weighting again")
    void scenarioIsKeptRefusedAndCleared() throws Exception {
        firm("Ranking Scenario Firm");
        String cfo = mandate();
        competencies(cfo, BRIEF);

        JsonNode saved = body(saveScenario(admin, cfo, null, 70, weights(50, 50, 100),
                "Board wants treasury depth")
                .andExpect(status().isOk())
                .andReturn());
        assertThat(saved.get("technicalShare").asInt()).isEqualTo(70);
        assertThat(saved.get("weights").get(TREASURY).asInt()).isEqualTo(50);
        assertThat(saved.get("rationale").asText()).isEqualTo("Board wants treasury depth");
        assertThat(saved.get("updatedByName").asText()).isEqualTo("Alok Kumar");
        assertThat(saved.get("briefChangedSinceSaved").asBoolean()).isFalse();
        assertThat(read(admin, cfo).get("scenario").get("version").asLong()).isEqualTo(saved.get("version").asLong());

        assertThat(codeOf(saveScenario(admin, cfo, null, 50, weights(60, 40, 100), null)
                .andExpect(status().isConflict()).andReturn())).isEqualTo("RANKING_SCENARIO_STALE");
        long version = saved.get("version").asLong();
        assertThat(codeOf(saveScenario(admin, cfo, version, 70, weights(50, 40, 100), null)
                .andExpect(status().isBadRequest()).andReturn())).isEqualTo("VALIDATION_FAILED");

        saveScenario(admin, cfo, version, 60, weights(60, 40, 100), "ignored on a baseline")
                .andExpect(status().isNoContent());
        assertThat(read(admin, cfo).get("scenario").isNull()).isTrue();

        long reopened = body(saveScenario(admin, cfo, null, 80, weights(60, 40, 100), null)
                .andExpect(status().isOk()).andReturn()).get("version").asLong();
        assertThat(reopened).isGreaterThan(version);
        assertThat(codeOf(saveScenario(admin, cfo, version, 30, weights(60, 40, 100), null)
                .andExpect(status().isConflict()).andReturn())).isEqualTo("RANKING_SCENARIO_STALE");

        assertThat(db.queryForObject("""
                SELECT count(*) FROM app_lm_audit_event
                WHERE event_type IN ('RANKING_SCENARIO_SAVED', 'RANKING_SCENARIO_CLEARED') AND target_id = ?""",
                Integer.class, cfo)).isEqualTo(3);
        assertThat(db.queryForObject("""
                SELECT count(*) FROM app_lm_audit_event
                WHERE target_id = ? AND metadata::text LIKE '%treasury depth%'""",
                Integer.class, cfo)).isZero();
    }

    @Test
    @DisplayName("once the brief moves, a saved scenario says so and a save naming the old competencies is refused")
    void briefChangedUnderTheScenario() throws Exception {
        firm("Ranking Brief Firm");
        String cfo = mandate();
        competencies(cfo, BRIEF);
        long version = body(saveScenario(admin, cfo, null, 70, weights(50, 50, 100), null)
                .andExpect(status().isOk()).andReturn()).get("version").asLong();

        competencies(cfo, """
                {"technical":[{"name":"Treasury","weight":60},{"name":"Tax","weight":40}],
                 "behavioural":[{"name":"Leadership","weight":100}]}""");

        JsonNode scenario = read(admin, cfo).get("scenario");
        assertThat(scenario.get("briefChangedSinceSaved").asBoolean()).isTrue();
        assertThat(scenario.get("droppedCriteria").get(0).asText()).isEqualTo("Reporting");
        assertThat(scenario.get("weights").get("TECHNICAL:tax").asInt()).isEqualTo(40);
        assertThat(scenario.get("weights").get(TREASURY).asInt()).isEqualTo(50);

        assertThat(codeOf(saveScenario(admin, cfo, version, 70, weights(50, 50, 100), null)
                .andExpect(status().isConflict()).andReturn())).isEqualTo("RANKING_BRIEF_CHANGED");
    }

    @Test
    @DisplayName("a client seat neither reads the ranking nor saves a scenario, and another firm's position is a 404")
    void clientSeatsAndOtherFirmsAreRefused() throws Exception {
        firm("Ranking Gate Firm");
        String cfo = mandate();
        competencies(cfo, BRIEF);

        String client = clientOn(cfo);
        mvc.perform(get(url(cfo)).header("Authorization", "Bearer " + client))
                .andExpect(status().isForbidden());
        saveScenario(client, cfo, null, 70, weights(50, 50, 100), null)
                .andExpect(status().isForbidden());
        mvc.perform(get(url(UUID.randomUUID().toString())).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
    }

    // ── fixture ──────────────────────────────────────────────────────────────

    private static Map<String, Integer> weights(int treasury, int reporting, int leadership) {
        Map<String, Integer> weights = new HashMap<>();
        weights.put(TREASURY, treasury);
        weights.put(REPORTING, reporting);
        weights.put(LEADERSHIP, leadership);
        return weights;
    }

    private ResultActions saveScenario(String token, String projectId, Long version, int technicalShare,
                                       Map<String, Integer> weights, String rationale) throws Exception {
        StringBuilder weightJson = new StringBuilder();
        weights.forEach((key, weight) -> weightJson.append(weightJson.isEmpty() ? "" : ",")
                .append('"').append(key).append("\":").append(weight));
        String rationaleJson = rationale == null ? "null" : '"' + rationale + '"';
        return mvc.perform(put(url(projectId) + "/scenario")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"version":%s,"technicalShare":%d,"weights":{%s},"rationale":%s}"""
                        .formatted(version, technicalShare, weightJson, rationaleJson)));
    }

    private JsonNode read(String token, String projectId) throws Exception {
        return body(mvc.perform(get(url(projectId)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
    }

    private JsonNode assessment(String projectId, String candidateId) throws Exception {
        return body(mvc.perform(get(assessmentUrl(projectId, candidateId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
    }

    private void assess(String projectId, String candidateId, boolean markReviewed, String scores) throws Exception {
        mvc.perform(put(assessmentUrl(projectId, candidateId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":null,"markReviewed":%s,"scores":%s,"report":{"concerns":"Notice period"}}"""
                                .formatted(markReviewed, scores)))
                .andExpect(status().isOk());
    }

    private void firm(String name) throws Exception {
        String address = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", address), name);
        admin = login(address);
    }

    private String mandate() throws Exception {
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Finance Unit"}"""))
                .andReturn()).get("id").asText();
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"Chief Financial Officer"}""".formatted(clientId)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private void competencies(String projectId, String body) throws Exception {
        mvc.perform(put("/api/v1/projects/" + projectId + "/position/competencies")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private String clientOn(String projectId) throws Exception {
        String clientEmail = "client@ranking-client-" + domain;
        mvc.perform(post("/api/v1/projects/" + projectId + "/representatives/invitations")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"A Client","position":"Chair","email":"%s"}
                                """.formatted(clientEmail)))
                .andExpect(status().isOk());
        return body(mvc.perform(post("/api/v1/onboarding/accept-invitation-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","fullName":"A Client","password":"%s"}
                                """.formatted(email.latestTokenFor(clientEmail), PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn()).get("accessToken").asText();
    }

    private String add(String projectId, String fullName) throws Exception {
        return body(mvc.perform(post("/api/v1/projects/" + projectId + "/candidates")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"%s"}""".formatted(fullName)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private static String url(String projectId) {
        return "/api/v1/projects/" + projectId + "/report/ranking";
    }

    private static String assessmentUrl(String projectId, String candidateId) {
        return "/api/v1/projects/" + projectId + "/candidates/" + candidateId + "/assessment";
    }
}
