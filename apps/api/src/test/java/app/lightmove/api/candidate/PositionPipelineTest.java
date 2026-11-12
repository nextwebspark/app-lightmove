package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * A position's Pipeline page (V132): everyone by stage and then the team's own order, a drag that writes
 * one row, the next action (the earliest open task, V133) and the client-visibility switch — each a timeline
 * line and an audit event — and none of it a client seat's.
 */
@IntegrationTest
class PositionPipelineTest extends PipelineFlowSupport {

    private String projectId;

    @BeforeEach
    void aPosition() throws Exception {
        firm("Pipeline Page Firm");
        projectId = mandate("Chief Financial Officer");
    }

    @Test
    @DisplayName("rows read by stage, then first mapped; each stage counts those in process, the rest inactive")
    void readsByStage() throws Exception {
        String layla = person("Layla Haddad", "contacted");
        String omar = person("Omar Said", "identified");
        String hana = person("Hana Aziz", "offLimits");
        String karim = person("Karim Nassar", "source");

        JsonNode pipeline = pipeline("");
        assertThat(ids(pipeline)).containsExactly(omar, hana, karim, layla);
        assertThat(count(pipeline, "identified")).isEqualTo(2);
        assertThat(count(pipeline, "qualified")).isZero();
        assertThat(pipeline.get("inactiveCount").asLong()).isEqualTo(1);
        assertThat(pipeline.at("/rows/1/outOfProcess").asBoolean()).isTrue();
        assertThat(pipeline.at("/rows/0/outOfProcess").asBoolean()).isFalse();
        assertThat(pipeline.at("/rows/3/statusLabel").asText()).isEqualTo("Awaiting reply");
        assertThat(pipeline.at("/rows/3/pipelineStage/key").asText()).isEqualTo("contacted");

        JsonNode research = pipeline("?pipelineStage=identified");
        assertThat(ids(research)).containsExactly(omar, hana, karim);
        assertThat(count(research, "contacted")).as("a stage filter leaves every pill's count").isEqualTo(1);

        formerClosedStage("Declined", "placedElsewhere");
        assertThat(pipeline("").get("stageCounts")).as("a stage of ways out alone is no column")
                .noneMatch(stage -> stage.get("key").asText().equals("declined"));

        JsonNode searched = pipeline("?q=layla");
        assertThat(ids(searched)).containsExactly(layla);
        assertThat(count(searched, "identified")).isZero();
        assertThat(searched.get("inactiveCount").asLong()).isZero();
    }

    @Test
    @DisplayName("a drag lands a row between its neighbours, and only between rows of its own stage")
    void aDragRanksOneRow() throws Exception {
        String first = person("First Person", "identified");
        String second = person("Second Person", "identified");
        String third = person("Third Person", "identified");
        String elsewhere = person("Contacted Person", "contacted");

        JsonNode row = body(write(admin, "PUT", rankUrl(third), """
                {"afterCandidateId":"%s","beforeCandidateId":"%s"}""".formatted(first, second)));
        assertThat(row.get("candidateId").asText()).isEqualTo(third);
        assertThat(ids(pipeline(""))).containsExactly(first, third, second, elsewhere);

        write(admin, "PUT", rankUrl(first), """
                {"afterCandidateId":"%s","beforeCandidateId":null}""".formatted(second));
        assertThat(ids(pipeline(""))).containsExactly(third, second, first, elsewhere);

        assertThat(codeOf(write(admin, "PUT", rankUrl(first), """
                {"afterCandidateId":"%s","beforeCandidateId":null}""".formatted(elsewhere))))
                .isEqualTo("PIPELINE_ORDER_INVALID");
    }

    @Test
    @DisplayName("two rows dropped into the same gap land in order, each on its own rank")
    void twoDropsIntoOneGapStayDistinct() throws Exception {
        String first = person("First Person", "identified");
        String second = person("Second Person", "identified");
        String third = person("Third Person", "identified");
        String fourth = person("Fourth Person", "identified");
        String sameGap = """
                {"afterCandidateId":"%s","beforeCandidateId":"%s"}""".formatted(first, second);

        write(admin, "PUT", rankUrl(third), sameGap);
        write(admin, "PUT", rankUrl(fourth), sameGap);

        assertThat(ids(pipeline(""))).containsExactly(first, fourth, third, second);
        assertThat(db.queryForObject("select count(distinct pipeline_rank) from app_lm_project_candidate "
                + "where project_id = ?::uuid", Integer.class, projectId)).isEqualTo(4);
    }

    @Test
    @DisplayName("a gap dragged into over and over is spread out again rather than halved away")
    void aSpentGapIsRankedWhole() throws Exception {
        String top = person("Top Person", "identified");
        String even = person("Even Person", "identified");
        String odd = person("Odd Person", "identified");

        for (int drag = 0; drag < 30; drag++) {
            String moved = drag % 2 == 0 ? odd : even;
            String below = drag % 2 == 0 ? even : odd;
            assertThat(write(admin, "PUT", rankUrl(moved), """
                    {"afterCandidateId":"%s","beforeCandidateId":"%s"}""".formatted(top, below))
                    .getResponse().getStatus()).isEqualTo(200);
            assertThat(ids(pipeline(""))).containsExactly(top, moved, below);
        }

        List<BigDecimal> ranks = db.queryForList("select pipeline_rank from app_lm_project_candidate "
                + "where project_id = ?::uuid order by pipeline_rank", BigDecimal.class, projectId);
        assertThat(ranks.get(1).subtract(ranks.get(0))).isGreaterThan(new BigDecimal("0.0004"));
        assertThat(ranks.get(2).subtract(ranks.get(1))).isGreaterThan(new BigDecimal("0.0004"));
    }

    @Test
    @DisplayName("a row cannot land between one neighbour named twice")
    void aDragNamesTwoDifferentNeighbours() throws Exception {
        String first = person("First Person", "identified");
        String second = person("Second Person", "identified");

        assertThat(write(admin, "PUT", rankUrl(second), """
                {"afterCandidateId":"%s","beforeCandidateId":"%s"}""".formatted(first, first))
                .getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("the next action is the earliest open task, and the client switch is a timeline line and an audit event")
    void workingFactsAreRecorded() throws Exception {
        String layla = person("Layla Haddad", "contacted");
        String me = adminUserId();

        write(admin, "POST", candidatesUrl(projectId) + "/" + layla + "/tasks", """
                {"title":"Reference check","kind":"todo","assigneeUserId":"%s","dueAt":"2026-11-09T09:00:00Z"}"""
                .formatted(me));
        write(admin, "POST", candidatesUrl(projectId) + "/" + layla + "/tasks", """
                {"title":"Call after the board meeting","kind":"call","assigneeUserId":"%s",
                 "dueAt":"2026-11-02T09:00:00Z"}""".formatted(me));
        JsonNode row = pipeline("").get("rows").get(0);
        assertThat(row.at("/nextAction/text").asText()).isEqualTo("Call after the board meeting");
        assertThat(row.at("/nextAction/kind").asText()).isEqualTo("call");
        assertThat(row.at("/nextAction/dueAt").asText()).isEqualTo("2026-11-02T09:00:00Z");
        assertThat(row.at("/nextAction/assignee/fullName").asText()).isEqualTo("Alok Kumar");
        assertThat(row.at("/nextAction/openCount").asInt()).isEqualTo(2);
        assertThat(row.at("/nextAction/setByName").asText()).isEqualTo("Alok Kumar");

        JsonNode hidden = body(write(admin, "PUT", candidatesUrl(projectId) + "/" + layla + "/client-visibility", """
                {"clientVisible":false}"""));
        assertThat(hidden.get("clientVisible").asBoolean()).isFalse();

        List<String> kinds = new ArrayList<>();
        body(mvc.perform(get(candidatesUrl(projectId) + "/" + layla + "/timeline")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn()).get("entries").forEach(entry -> kinds.add(entry.get("kind").asText()));
        assertThat(kinds).contains("TASK_CREATED", "CLIENT_VISIBILITY_CHANGED");
        assertThat(db.queryForList("""
                select event_type from app_lm_audit_event where workspace_id = ?::uuid
                  and event_type in ('TASK_CREATED', 'CANDIDATE_CLIENT_VISIBILITY_CHANGED')
                order by occurred_at""", String.class, workspaceId))
                .containsExactly("TASK_CREATED", "TASK_CREATED", "CANDIDATE_CLIENT_VISIBILITY_CHANGED");
    }

    @Test
    @DisplayName("a client seat reads none of the Pipeline page and changes none of it")
    void aClientSeatIsKeptOut() throws Exception {
        String layla = person("Layla Haddad", "contacted");
        String client = clientOn(projectId);

        assertThat(mvc.perform(get("/api/v1/projects/" + projectId + "/pipeline")
                .header("Authorization", "Bearer " + client)).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(write(client, "PUT", candidatesUrl(projectId) + "/" + layla + "/client-visibility", """
                {"clientVisible":false}""").getResponse().getStatus()).isEqualTo(403);
        assertThat(write(client, "POST", candidatesUrl(projectId) + "/" + layla + "/tasks", """
                {"title":"Nudge them","kind":"todo","assigneeUserId":"%s","dueAt":"2026-11-02T09:00:00Z"}"""
                .formatted(adminUserId())).getResponse().getStatus()).isEqualTo(403);
        assertThat(write(client, "PUT", rankUrl(layla), """
                {"afterCandidateId":null,"beforeCandidateId":null}""").getResponse().getStatus()).isEqualTo(403);
    }

    private String person(String fullName, String statusKey) throws Exception {
        return add(projectId, """
                {"fullName":"%s","status":"%s"}""".formatted(fullName, statusKey)).get("id").asText();
    }

    private String adminUserId() throws Exception {
        return body(mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn()).at("/id").asText();
    }

    private JsonNode pipeline(String query) throws Exception {
        return body(mvc.perform(get("/api/v1/projects/" + projectId + "/pipeline" + query)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
    }

    private String rankUrl(String candidateId) {
        return candidatesUrl(projectId) + "/" + candidateId + "/pipeline-rank";
    }

    private static List<String> ids(JsonNode pipeline) {
        List<String> ids = new ArrayList<>();
        pipeline.get("rows").forEach(row -> ids.add(row.get("candidateId").asText()));
        return ids;
    }

    private static long count(JsonNode pipeline, String stageKey) {
        for (JsonNode stage : pipeline.get("stageCounts")) {
            if (stage.get("key").asText().equals(stageKey)) {
                return stage.get("count").asLong();
            }
        }
        throw new AssertionError("no count for " + stageKey);
    }
}
