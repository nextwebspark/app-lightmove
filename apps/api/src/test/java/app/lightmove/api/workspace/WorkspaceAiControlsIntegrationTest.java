package app.lightmove.api.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/** #922: what the AI may do with a workspace's people is the workspace's choice, and off clears what it left. */
@IntegrationTest
class WorkspaceAiControlsIntegrationTest extends FlowTestSupport {

    @Autowired @FixtureJdbc JdbcTemplate jdbc;

    @Test
    @DisplayName("a workspace founded since V213 starts with every AI switch off")
    void aNewWorkspaceStartsWithEverySwitchOff() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Careful Firm");

        mvc.perform(get("/api/v1/workspace").header("Authorization", "Bearer " + login(alok)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiControls.inferGender").value(false))
                .andExpect(jsonPath("$.aiControls.inferNationality").value(false))
                .andExpect(jsonPath("$.aiControls.draftAssessments").value(false))
                .andExpect(jsonPath("$.aiControls.enrichAutomatically").value(false));
    }

    @Test
    @DisplayName("an admin turns a switch on, the change is on the record, and the same choice records nothing")
    void anAdminChangesTheSwitchesOnTheRecord() throws Exception {
        String alok = "alok@" + domain;
        String workspaceId = createWorkspace(verifiedUser("Alok Kumar", alok), "Switching Firm");
        String admin = login(alok);

        changeAiControls(admin, false, false, true, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiControls.draftAssessments").value(true))
                .andExpect(jsonPath("$.aiControls.enrichAutomatically").value(true))
                .andExpect(jsonPath("$.aiControls.inferGender").value(false));
        changeAiControls(admin, false, false, true, true).andExpect(status().isOk());

        assertThat(changesRecordedIn(workspaceId)).hasSize(1);
        Map<String, Object> change = changesRecordedIn(workspaceId).getFirst();
        assertThat(change.get("draftAssessments")).isEqualTo("on");
        assertThat(change.get("enrichAutomatically")).isEqualTo("on");
        assertThat(change.get("inferGender")).isNull();
    }

    @Test
    @DisplayName("members and client representatives may neither change the switches nor count what they would clear")
    void refusedForMemberAndClient() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Guarded AI Firm");
        String admin = login(alok);
        inviteAndAccept(admin, "Sara Al-Mansour", "sara@" + domain, "MEMBER");
        String client = clientRepresentative(admin, "Rana Client", "rana@client-" + domain);
        String member = login("sara@" + domain);

        changeAiControls(member, true, true, true, true).andExpect(status().isForbidden());
        changeAiControls(client, true, true, true, true).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/workspace/ai-controls/inferred-background")
                        .header("Authorization", "Bearer " + member))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/workspace").header("Authorization", "Bearer " + member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiControls.inferGender").value(false));
    }

    @Test
    @DisplayName("turning gender and nationality off clears only the AI's unconfirmed values, in this workspace alone")
    void turningInferenceOffClearsOnlyTheAiValues() throws Exception {
        String alok = "alok@" + domain;
        String workspaceId = createWorkspace(verifiedUser("Alok Kumar", alok), "Clearing Firm");
        switchOnPeopleAi(workspaceId);
        String admin = login(alok);
        String guessed = person(workspaceId, "Guessed Person", "FEMALE", "Emirati", "[\"gender\", \"nationality\"]",
                true);
        String recorded = person(workspaceId, "Recorded Person", "MALE", "Saudi", "[]", false);
        String readOnly = person(workspaceId, "Read Person", null, null, "[]", true);

        String nadia = "nadia@other-" + domain;
        String otherWorkspaceId = createWorkspace(verifiedUser("Nadia Rahman", nadia), "Other Firm");
        String elsewhere = person(otherWorkspaceId, "Elsewhere Person", "FEMALE", "Qatari",
                "[\"gender\", \"nationality\"]", true);

        mvc.perform(get("/api/v1/workspace/ai-controls/inferred-background")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.genderValues").value(1))
                .andExpect(jsonPath("$.nationalityValues").value(2));

        changeAiControls(admin, false, false, true, true).andExpect(status().isOk());

        assertThat(background(guessed)).containsEntry("gender", null).containsEntry("nationality", null)
                .containsEntry("reading", null).containsEntry("inferred", "[]");
        assertThat(background(recorded)).containsEntry("gender", "MALE").containsEntry("nationality", "Saudi");
        assertThat(background(readOnly)).containsEntry("reading", null);
        assertThat(background(elsewhere)).containsEntry("gender", "FEMALE").containsEntry("nationality", "Qatari");
        Map<String, Object> change = changesRecordedIn(workspaceId).getLast();
        assertThat(change.get("inferGender")).isEqualTo("off");
        assertThat(change.get("genderCleared")).isEqualTo("1");
        assertThat(change.get("nationalityCleared")).isEqualTo("2");
    }

    private ResultActions changeAiControls(String bearerToken, boolean inferGender, boolean inferNationality,
                                           boolean draftAssessments, boolean enrichAutomatically) throws Exception {
        return mvc.perform(put("/api/v1/workspace/ai-controls")
                .header("Authorization", "Bearer " + bearerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"inferGender":%s,"inferNationality":%s,"draftAssessments":%s,"enrichAutomatically":%s}"""
                        .formatted(inferGender, inferNationality, draftAssessments, enrichAutomatically)));
    }

    private String person(String workspaceId, String fullName, String gender, String nationality, String inferred,
                          boolean withReading) {
        return jdbc.queryForObject("""
                INSERT INTO app_lm_person (workspace_id, full_name, gender, nationality, ai_inferred_fields,
                                           ai_nationality_reading, source, created_by)
                SELECT id, ?, ?, ?, ?::jsonb, ?::jsonb, 'MANUAL', created_by FROM app_lm_workspace WHERE id = ?::uuid
                RETURNING id::text
                """, String.class, fullName, gender, nationality, inferred,
                withReading ? "{\"category\":\"Emirati\",\"confidence\":\"HIGH\"}" : null, workspaceId);
    }

    private Map<String, Object> background(String personId) {
        return jdbc.queryForMap("""
                SELECT gender, nationality, ai_nationality_reading::text AS reading, ai_inferred_fields::text AS inferred
                FROM app_lm_person WHERE id = ?::uuid
                """, personId);
    }

    private List<Map<String, Object>> changesRecordedIn(String workspaceId) {
        return jdbc.queryForList("""
                SELECT event.metadata ->> 'inferGender' AS "inferGender",
                       event.metadata ->> 'draftAssessments' AS "draftAssessments",
                       event.metadata ->> 'enrichAutomatically' AS "enrichAutomatically",
                       event.metadata ->> 'genderCleared' AS "genderCleared",
                       event.metadata ->> 'nationalityCleared' AS "nationalityCleared"
                FROM app_lm_audit_event event
                WHERE event.event_type = 'WORKSPACE_UPDATED' AND event.workspace_id = ?::uuid
                  AND event.metadata ->> 'section' = 'aiControls'
                ORDER BY event.occurred_at, event.id
                """, workspaceId);
    }
}
