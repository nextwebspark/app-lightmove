package app.lightmove.api.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.ResultActions;

/** How a workspace's calendar events are read: through Recall by default, or directly where IT will not share. */
@IntegrationTest
class CalendarSyncIntegrationTest extends FlowTestSupport {

    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("a new workspace reads its calendars through Recall")
    void defaultsToRecall() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Recall Default Firm");

        mvc.perform(get("/api/v1/workspace").header("Authorization", "Bearer " + login(alok)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calendarSync").value("RECALL"));
    }

    @Test
    @DisplayName("an admin switches to direct reads, and the switch is on the record")
    void adminSwitchesToDirect() throws Exception {
        String alok = "alok@" + domain;
        String workspaceId = createWorkspace(verifiedUser("Alok Kumar", alok), "Direct Firm");
        String admin = login(alok);

        changeCalendarSync(admin, "DIRECT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calendarSync").value("DIRECT"));

        mvc.perform(get("/api/v1/workspace").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.calendarSync").value("DIRECT"));
        assertThat(switchesRecordedIn(workspaceId, "RECALL", "DIRECT")).isEqualTo(1);
    }

    @Test
    @DisplayName("choosing what is already in force records nothing")
    void sameChoiceRecordsNothing() throws Exception {
        String alok = "alok@" + domain;
        String workspaceId = createWorkspace(verifiedUser("Alok Kumar", alok), "Steady Calendar Firm");

        changeCalendarSync(login(alok), "RECALL").andExpect(status().isOk());

        assertThat(switchesRecordedIn(workspaceId, "RECALL", "RECALL")).isZero();
    }

    @Test
    @DisplayName("members and client representatives may not change it")
    void refusedForMemberAndClient() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Guarded Calendar Firm");
        String admin = login(alok);
        inviteAndAccept(admin, "Sara Al-Mansour", "sara@" + domain, "MEMBER");
        String client = clientRepresentative(admin, "Rana Client", "rana@client-" + domain);

        changeCalendarSync(login("sara@" + domain), "DIRECT").andExpect(status().isForbidden());
        changeCalendarSync(client, "DIRECT").andExpect(status().isForbidden());
    }

    private ResultActions changeCalendarSync(String bearerToken, String choice) throws Exception {
        return mvc.perform(put("/api/v1/workspace/calendar-sync")
                .header("Authorization", "Bearer " + bearerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"calendarSync":"%s"}""".formatted(choice)));
    }

    private int switchesRecordedIn(String workspaceId, String from, String to) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM app_lm_audit_event event
                WHERE event.event_type = 'WORKSPACE_UPDATED' AND event.workspace_id = ?::uuid
                  AND event.metadata ->> 'section' = 'calendarSync'
                  AND event.metadata ->> 'from' = ? AND event.metadata ->> 'to' = ?
                """, Integer.class, workspaceId, from, to);
        return count == null ? 0 : count;
    }
}
