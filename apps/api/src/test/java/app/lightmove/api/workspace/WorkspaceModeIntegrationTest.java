package app.lightmove.api.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/** Whether a workspace hires for client companies or its own business units: chosen at creation, switched by an admin. */
@IntegrationTest
class WorkspaceModeIntegrationTest extends FlowTestSupport {

    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("a workspace cannot be founded without saying who it hires for")
    void creationRequiresAMode() throws Exception {
        String founder = verifiedUser("Alok Kumar", "alok@" + domain);

        MvcResult refused = mvc.perform(post("/api/v1/onboarding/workspace")
                        .header("Authorization", "Bearer " + founder)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Undecided Firm"}"""))
                .andReturn();

        assertThat(refused.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(refused)).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("the chosen mode rides on /me and on the settings read")
    void chosenModeIsReadBack() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Gulf Search Partners", "AGENCY");
        String admin = login(alok);

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspace.mode").value("AGENCY"));
        mvc.perform(get("/api/v1/workspace").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("AGENCY"));
    }

    @Test
    @DisplayName("an admin switches the mode, and the switch is on the record")
    void adminSwitchesMode() throws Exception {
        String alok = "alok@" + domain;
        String workspaceId = createWorkspace(verifiedUser("Alok Kumar", alok), "Switching Firm");
        String admin = login(alok);

        mvc.perform(put("/api/v1/workspace/mode")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"AGENCY"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("AGENCY"));

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.workspace.mode").value("AGENCY"));
        assertThat(modeSwitchesRecordedIn(workspaceId, "COMPANY", "AGENCY")).isEqualTo(1);
    }

    @Test
    @DisplayName("switching to the mode already in force records nothing")
    void sameModeRecordsNothing() throws Exception {
        String alok = "alok@" + domain;
        String workspaceId = createWorkspace(verifiedUser("Alok Kumar", alok), "Steady Firm");

        mvc.perform(put("/api/v1/workspace/mode")
                        .header("Authorization", "Bearer " + login(alok))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"COMPANY"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("COMPANY"));

        assertThat(modeSwitchesRecordedIn(workspaceId, "COMPANY", "COMPANY")).isZero();
    }

    @Test
    @DisplayName("a mode changed by the signup wizard's Back leaves the same record as one changed in Settings")
    void wizardSwitchIsOnTheRecord() throws Exception {
        String alok = "alok@" + domain;
        String workspaceId = createWorkspace(verifiedUser("Alok Kumar", alok), "Wizard Firm");
        String admin = login(alok);

        redescribe(admin, "AGENCY");
        redescribe(admin, "AGENCY");

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.workspace.mode").value("AGENCY"));
        assertThat(modeSwitchesRecordedIn(workspaceId, "COMPANY", "AGENCY")).isEqualTo(1);
        assertThat(modeSwitchesRecordedIn(workspaceId, "AGENCY", "AGENCY")).isZero();
    }

    @Test
    @DisplayName("a member may not switch the mode")
    void memberCannotSwitchMode() throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Guarded Mode Firm");
        inviteAndAccept(login(alok), "Sara Al-Mansour", sara, "MEMBER");

        mvc.perform(put("/api/v1/workspace/mode")
                        .header("Authorization", "Bearer " + login(sara))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"AGENCY"}"""))
                .andExpect(status().isForbidden());
    }

    private void redescribe(String bearerToken, String mode) throws Exception {
        mvc.perform(patch("/api/v1/onboarding/workspace")
                        .header("Authorization", "Bearer " + bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"%s","name":"Wizard Firm"}
                                """.formatted(mode)))
                .andExpect(status().isOk());
    }

    private int modeSwitchesRecordedIn(String workspaceId, String from, String to) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM app_lm_audit_event event
                WHERE event.event_type = 'WORKSPACE_UPDATED' AND event.workspace_id = ?::uuid
                  AND event.metadata ->> 'section' = 'mode'
                  AND event.metadata ->> 'from' = ? AND event.metadata ->> 'to' = ?
                """, Integer.class, workspaceId, from, to);
        return count == null ? 0 : count;
    }
}
