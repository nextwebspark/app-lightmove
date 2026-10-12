package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * A position's Pipeline page (V132): everyone by stage and then the team's own order, a drag that writes
 * one row, the next action and the client-visibility switch — each a timeline line and an audit event —
 * and none of it a client seat's.
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
    @DisplayName("rows read by stage, then first mapped; every live stage is counted, the search honoured")
    void readsByStage() throws Exception {
        String layla = person("Layla Haddad", "contacted");
        String omar = person("Omar Said", "identified");
        String hana = person("Hana Aziz", "offLimits");
        String karim = person("Karim Nassar", "researching");

        JsonNode pipeline = pipeline("");
        assertThat(ids(pipeline)).containsExactly(omar, karim, layla, hana);
        assertThat(count(pipeline, "identified")).isEqualTo(2);
        assertThat(count(pipeline, "qualified")).isZero();
        assertThat(count(pipeline, "declined")).isEqualTo(1);
        assertThat(pipeline.at("/rows/2/statusLabel").asText()).isEqualTo("Contacted");
        assertThat(pipeline.at("/rows/2/pipelineStage/key").asText()).isEqualTo("contacted");

        JsonNode declined = pipeline("?pipelineStage=declined");
        assertThat(ids(declined)).containsExactly(hana);
        assertThat(count(declined, "identified")).as("a stage filter leaves every pill's count").isEqualTo(2);

        JsonNode searched = pipeline("?q=layla");
        assertThat(ids(searched)).containsExactly(layla);
        assertThat(count(searched, "identified")).isZero();
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
    @DisplayName("the next action and the client switch are each a timeline line and an audit event")
    void workingFactsAreRecorded() throws Exception {
        String layla = person("Layla Haddad", "contacted");

        JsonNode planned = body(write(admin, "PUT", candidatesUrl(projectId) + "/" + layla + "/next-action", """
                {"text":"Call after the board meeting","dueAt":"2026-11-02T09:00:00Z"}"""));
        assertThat(planned.at("/nextAction/text").asText()).isEqualTo("Call after the board meeting");
        assertThat(planned.at("/nextAction/dueAt").asText()).isEqualTo("2026-11-02T09:00:00Z");
        assertThat(planned.at("/nextAction/setByName").asText()).isEqualTo("Alok Kumar");

        JsonNode hidden = body(write(admin, "PUT", candidatesUrl(projectId) + "/" + layla + "/client-visibility", """
                {"clientVisible":false}"""));
        assertThat(hidden.get("clientVisible").asBoolean()).isFalse();

        JsonNode cleared = body(write(admin, "PUT", candidatesUrl(projectId) + "/" + layla + "/next-action", """
                {"text":"  "}"""));
        assertThat(cleared.get("nextAction").isNull()).isTrue();

        List<String> kinds = new ArrayList<>();
        body(mvc.perform(get(candidatesUrl(projectId) + "/" + layla + "/timeline")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn()).get("entries").forEach(entry -> kinds.add(entry.get("kind").asText()));
        assertThat(kinds).contains("NEXT_ACTION_SET", "CLIENT_VISIBILITY_CHANGED");
        assertThat(db.queryForList("""
                select event_type from app_lm_audit_event where workspace_id = ?::uuid
                  and event_type in ('CANDIDATE_NEXT_ACTION_SET', 'CANDIDATE_CLIENT_VISIBILITY_CHANGED')
                order by occurred_at""", String.class, workspaceId))
                .containsExactly("CANDIDATE_NEXT_ACTION_SET", "CANDIDATE_CLIENT_VISIBILITY_CHANGED",
                        "CANDIDATE_NEXT_ACTION_SET");
        assertThat(write(admin, "PUT", candidatesUrl(projectId) + "/" + layla + "/next-action",
                "{\"text\":\"" + "x".repeat(201) + "\"}").getResponse().getStatus()).isEqualTo(400);
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
        assertThat(write(client, "PUT", candidatesUrl(projectId) + "/" + layla + "/next-action", """
                {"text":"Nudge them"}""").getResponse().getStatus()).isEqualTo(403);
        assertThat(write(client, "PUT", rankUrl(layla), """
                {"afterCandidateId":null,"beforeCandidateId":null}""").getResponse().getStatus()).isEqualTo(403);
    }

    private String person(String fullName, String statusKey) throws Exception {
        return add(projectId, """
                {"fullName":"%s","status":"%s"}""".formatted(fullName, statusKey)).get("id").asText();
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
