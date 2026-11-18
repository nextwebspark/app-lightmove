package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Why a candidate left the process (V228): asked for on a move to a way out that offers reasons, changed in place,
 * cleared by any further move, and read by staff alone.
 */
@IntegrationTest
class CandidateExitReasonTest extends PipelineFlowSupport {

    private String projectId;
    private String candidateId;
    private JsonNode reasons;

    @BeforeEach
    void aCandidateInProcess() throws Exception {
        firm("Exit Reason Capture Firm");
        projectId = mandate("Chief Financial Officer");
        candidateId = add(projectId, """
                {"fullName":"Layla Haddad","status":"contacted"}""").get("id").asText();
        reasons = body(mvc.perform(get(PIPELINE + "/reasons").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn());
    }

    @Test
    @DisplayName("a way out that offers reasons is given one it offers, and a note where the reason asks for one")
    void aMoveOutAsksWhy() throws Exception {
        assertThat(codeOf(move("notInterested", null, null))).isEqualTo("EXIT_REASON_REQUIRED");
        assertThat(codeOf(move("notInterested", reason("negativeFeedback"), null)))
                .as("Negative feedback is the client's, not offered at Not interested")
                .isEqualTo("EXIT_REASON_NOT_OFFERED");
        assertThat(codeOf(move("notInterested", reason("other"), "  "))).isEqualTo("EXIT_REASON_NOTE_REQUIRED");
        assertThat(codeOf(move("engaged", reason("compensation"), null)))
                .as("a status in process takes no reason").isEqualTo("EXIT_REASON_NOT_OFFERED");
        assertThat(exitReasons()).as("a refused move changes nothing").isEmpty();

        assertThat(move("notInterested", reason("other"), " Relocating to Riyadh ").getResponse().getStatus())
                .isEqualTo(200);

        JsonNode given = exitReasons().get(0);
        assertThat(given.get("candidateId").asText()).isEqualTo(candidateId);
        assertThat(given.get("label").asText()).isEqualTo("Other");
        assertThat(given.get("note").asText()).isEqualTo("Relocating to Riyadh");
        assertThat(given.get("setByName").isNull()).isFalse();
        assertThat(pipelineRow().at("/exitReason/label").asText()).isEqualTo("Other");
        assertThat(pipelineRow().at("/exitReason/requiresNote").asBoolean()).isTrue();
        assertThat(codeOf(move("notInterested", reason("compensation"), null)))
                .as("a reason rides a move; the status already held changes it through PUT exit-reason")
                .isEqualTo("EXIT_REASON_NOT_OFFERED");
    }

    @Test
    @DisplayName("an executive added, or saved, straight out of process is asked why like any other move")
    void anAddOutOfProcessAsksWhy() throws Exception {
        assertThat(codeOf(write(admin, "POST", candidatesUrl(projectId), """
                {"fullName":"Omar Nasser","status":"notInterested"}"""))).isEqualTo("EXIT_REASON_REQUIRED");
        assertThat(codeOf(write(admin, "POST", candidatesUrl(projectId), """
                {"fullName":"Omar Nasser","status":"notInterested","exitReasonId":"%s","exitNote":" "}"""
                .formatted(reason("other"))))).isEqualTo("EXIT_REASON_NOTE_REQUIRED");
        assertThat(codeOf(write(admin, "POST", candidatesUrl(projectId), """
                {"fullName":"Omar Nasser","status":"contacted","exitReasonId":"%s"}"""
                .formatted(reason("timing"))))).as("a status in process takes no reason")
                .isEqualTo("EXIT_REASON_NOT_OFFERED");
        assertThat(exitReasons()).as("a refused add files nobody's reason").isEmpty();

        String omar = add(projectId, """
                {"fullName":"Omar Nasser","status":"notInterested","exitReasonId":"%s"}"""
                .formatted(reason("timing"))).get("id").asText();
        assertThat(exitReasons()).singleElement().satisfies(given -> {
            assertThat(given.get("candidateId").asText()).isEqualTo(omar);
            assertThat(given.get("label").asText()).isEqualTo("Timing");
        });

        assertThat(codeOf(write(admin, "PUT", candidatesUrl(projectId) + "/" + candidateId, """
                {"fullName":"Layla Haddad","status":"withdrew"}"""))).isEqualTo("EXIT_REASON_REQUIRED");
        assertThat(write(admin, "PUT", candidatesUrl(projectId) + "/" + candidateId, """
                {"fullName":"Layla Haddad","status":"withdrew","exitReasonId":"%s"}"""
                .formatted(reason("anotherOffer"))).getResponse().getStatus()).isEqualTo(200);
        assertThat(pipelineRow().at("/exitReason/label").asText()).isEqualTo("Another offer");
    }

    @Test
    @DisplayName("a way out that offers no reasons asks for none, and refuses one")
    void aWayOutWithNoReasonsAsksNone() throws Exception {
        assertThat(move("offLimits", null, null).getResponse().getStatus()).isEqualTo(200);
        assertThat(exitReasons()).isEmpty();
        assertThat(codeOf(move("unresponsive", reason("timing"), null))).isEqualTo("EXIT_REASON_NOT_OFFERED");
    }

    @Test
    @DisplayName("the reason is changed in place, and any further move clears it")
    void theReasonIsChangedThenCleared() throws Exception {
        move("notInterested", reason("compensation"), null);

        JsonNode changed = body(write(admin, "PUT", reasonUrl(), """
                {"reasonId":"%s"}""".formatted(reason("timing"))));
        assertThat(changed.get("label").asText()).isEqualTo("Timing");
        JsonNode lines = body(mvc.perform(get(candidatesUrl(projectId) + "/" + candidateId + "/timeline")
                .header("Authorization", "Bearer " + admin)).andExpect(status().isOk()).andReturn()).get("entries");
        assertThat(lines.get(0).get("kind").asText()).isEqualTo("EXIT_REASON_CHANGED");
        assertThat(lines.get(0).at("/details/fromReason").asText()).isEqualTo("Compensation");
        assertThat(lines.get(0).at("/details/reason").asText()).isEqualTo("Timing");
        assertThat(lines.get(1).at("/details/reason").asText()).as("the move said why").isEqualTo("Compensation");
        assertThat(codeOf(write(admin, "PUT", reasonUrl(), """
                {"reasonId":"%s"}""".formatted(reason("negativeFeedback"))))).isEqualTo("EXIT_REASON_NOT_OFFERED");
        assertThat(codeOf(write(admin, "PUT", reasonUrl(), """
                {"reasonId":"%s"}""".formatted(reason("other"))))).isEqualTo("EXIT_REASON_NOTE_REQUIRED");

        assertThat(moveTo(projectId, candidateId, "engaged").getResponse().getStatus()).isEqualTo(200);
        assertThat(exitReasons()).as("back in process, the reason goes").isEmpty();
        assertThat(codeOf(write(admin, "PUT", reasonUrl(), """
                {"reasonId":"%s"}""".formatted(reason("timing"))))).isEqualTo("EXIT_REASON_NOT_OFFERED");
    }

    @Test
    @DisplayName("a reason retired after it was given stays, and its note can still be corrected")
    void aRetiredReasonIsKept() throws Exception {
        move("notInterested", reason("compensation"), null);
        write(admin, "PATCH", PIPELINE + "/reasons/" + reason("compensation"), """
                {"retired":true}""");

        JsonNode kept = body(write(admin, "PUT", reasonUrl(), """
                {"reasonId":"%s","note":"Asked for a third more"}""".formatted(reason("compensation"))));
        assertThat(kept.get("retired").asBoolean()).isTrue();
        assertThat(kept.get("note").asText()).isEqualTo("Asked for a third more");

        moveTo(projectId, candidateId, "engaged");
        assertThat(codeOf(move("notInterested", reason("compensation"), null))).isEqualTo("EXIT_REASON_RETIRED");
    }

    @Test
    @DisplayName("a client seat is not told why anyone left")
    void aClientSeatReadsNoReason() throws Exception {
        move("notInterested", reason("compensation"), null);
        String client = clientOn(projectId);

        assertThat(mvc.perform(get(candidatesUrl(projectId) + "/exit-reasons")
                .header("Authorization", "Bearer " + client)).andReturn().getResponse().getStatus())
                .isIn(403, 404);
        JsonNode candidate = body(mvc.perform(get(candidatesUrl(projectId) + "/" + candidateId)
                .header("Authorization", "Bearer " + admin)).andExpect(status().isOk()).andReturn());
        assertThat(candidate.has("exitReason")).as("the candidate a client reads carries no reason").isFalse();
    }

    @Test
    @DisplayName("a reason someone holds cannot be deleted, and their workspace's deletion still takes both")
    void aHeldReasonOutlivesOnlyItsWorkspace() throws Exception {
        move("notInterested", reason("other"), "Moving to Riyadh");

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> db.update(
                "DELETE FROM app_lm_workspace_exit_reason WHERE id = ?::uuid", reason("other"))))
                .as("retired, never deleted").isNotNull();
        db.update("DELETE FROM app_lm_workspace WHERE id = ?::uuid", workspaceId);
        assertThat(db.queryForObject("SELECT count(*) FROM app_lm_project_candidate WHERE id = ?::uuid",
                Integer.class, candidateId)).isZero();
    }

    private org.springframework.test.web.servlet.MvcResult move(String statusKey, String reasonId, String note)
            throws Exception {
        String reasonJson = reasonId == null ? "" : ",\"reasonId\":\"" + reasonId + "\"";
        String noteJson = note == null ? "" : ",\"note\":\"" + note + "\"";
        return write(admin, "PATCH", candidatesUrl(projectId) + "/" + candidateId, """
                {"status":"%s"%s%s}""".formatted(statusKey, reasonJson, noteJson));
    }

    private String reason(String key) {
        for (JsonNode reason : reasons.get("reasons")) {
            if (reason.get("key").asText().equals(key)) {
                return reason.get("id").asText();
            }
        }
        throw new AssertionError("no reason " + key);
    }

    private String reasonUrl() {
        return candidatesUrl(projectId) + "/" + candidateId + "/exit-reason";
    }

    private JsonNode exitReasons() throws Exception {
        return body(mvc.perform(get(candidatesUrl(projectId) + "/exit-reasons")
                .header("Authorization", "Bearer " + admin)).andExpect(status().isOk()).andReturn());
    }

    private JsonNode pipelineRow() throws Exception {
        JsonNode pipeline = body(mvc.perform(get("/api/v1/projects/" + projectId + "/pipeline")
                .header("Authorization", "Bearer " + admin)).andExpect(status().isOk()).andReturn());
        for (JsonNode row : pipeline.get("rows")) {
            if (row.get("candidateId").asText().equals(candidateId)) {
                return row;
            }
        }
        throw new AssertionError("no row " + candidateId);
    }
}
