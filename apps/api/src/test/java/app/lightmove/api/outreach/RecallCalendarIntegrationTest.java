package app.lightmove.api.outreach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingMailboxGateway;
import app.lightmove.api.RecordingProviderTokenClient;
import app.lightmove.api.RecordingRecallCalendarApi;
import app.lightmove.api.outreach.model.GrantedMailbox;
import app.lightmove.api.outreach.model.MailboxGrants;
import app.lightmove.api.outreach.model.RecallCalendarSpec;
import app.lightmove.api.outreach.service.OutreachDispatcher;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * A direct mailbox's Recall calendar, through every turn of its life: made on connect, handed the new token on a
 * reconnect, deleted on a disconnect, a workspace switch to DIRECT and a Recall-reported disconnection — and the
 * refresh token kept only as ciphertext throughout.
 */
@IntegrationTest
class RecallCalendarIntegrationTest extends FlowTestSupport {

    private static final String MAILBOX = "/api/v1/outreach/mailbox";
    private static final String ADDRESS = "consultant@firm.example";

    @Autowired private RecordingMailboxGateway gateway;
    @Autowired private RecordingRecallCalendarApi recall;
    @Autowired private RecordingProviderTokenClient tokenEndpoint;
    @Autowired private OutreachDispatcher dispatcher;
    @Autowired private JdbcTemplate jdbc;

    private String consultant;
    private String workspaceId;

    @BeforeEach
    void signInConsultant() throws Exception {
        gateway.clear();
        recall.clear();
        tokenEndpoint.clear();
        String email = "yara@" + domain;
        createWorkspace(verifiedUser("Yara Haddad", email), "Meridian Search");
        consultant = login(email);
        workspaceId = jdbc.queryForObject("select w.id::text from app_lm_workspace w join app_lm_workspace_member m "
                + "on m.workspace_id = w.id join app_lm_user u on u.id = m.user_id where u.email = ?", String.class,
                email);
    }

    @Test
    @DisplayName("a direct mailbox gets a Recall calendar with the shared app's keys, and its token is stored sealed")
    void connectingMakesTheCalendar() throws Exception {
        connectDirect("refresh-token-1");

        assertThat(recall.createdSpecs()).hasSize(1);
        RecallCalendarSpec spec = recall.createdSpecs().getFirst();
        assertThat(spec.platform()).isEqualTo("google_calendar");
        assertThat(spec.clientId()).isEqualTo("uncava-google-client");
        assertThat(spec.clientSecret()).isEqualTo("uncava-google-secret");
        assertThat(spec.refreshToken()).isEqualTo("refresh-token-1");
        assertThat(spec.email()).isEqualTo(ADDRESS);

        Map<String, Object> row = connectionRow();
        assertThat(row.get("gateway")).isEqualTo("DIRECT");
        assertThat(recall.holds((String) row.get("recall_calendar_id"))).isTrue();
        assertThat((String) row.get("refresh_token_encrypted")).isNotBlank().doesNotContain("refresh-token-1");
        mvc.perform(get(MAILBOX).header("Authorization", "Bearer " + consultant))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain("refresh-token-1").doesNotContain("refresh_token"));
    }

    @Test
    @DisplayName("a reconnect hands the same calendar the new token; a disconnect deletes it")
    void reconnectUpdatesAndDisconnectDeletes() throws Exception {
        connectDirect("refresh-token-1");
        String calendarId = recallCalendarId();

        connectDirect("refresh-token-2");
        assertThat(recall.createdSpecs()).hasSize(1);
        assertThat(recall.updates()).singleElement().satisfies(update -> {
            assertThat(update.calendarId()).isEqualTo(calendarId);
            assertThat(update.spec().refreshToken()).isEqualTo("refresh-token-2");
        });
        assertThat(recallCalendarId()).isEqualTo(calendarId);

        mvc.perform(delete(MAILBOX).header("Authorization", "Bearer " + consultant))
                .andExpect(status().isNoContent());
        assertThat(recall.deleted()).containsExactly(calendarId);
        assertThat(tokenEndpoint.revoked())
                .as("the reconnect's replaced grant, then the disconnected one, each revoked by its own token")
                .containsExactly("GOOGLE:refresh-token-1", "GOOGLE:refresh-token-2");
    }

    @Test
    @DisplayName("moving the workspace to DIRECT deletes its Recall calendars, and moving back makes them again")
    void switchingCalendarSync() throws Exception {
        connectDirect("refresh-token-1");
        String first = recallCalendarId();

        switchCalendarSync("DIRECT");
        assertThat(recall.deleted()).containsExactly(first);
        assertThat(recallCalendarId()).isNull();

        switchCalendarSync("RECALL");
        assertThat(recall.createdSpecs()).hasSize(2);
        assertThat(recallCalendarId()).isNotNull().isNotEqualTo(first);
    }

    @Test
    @DisplayName("Recall reporting the calendar disconnected marks the mailbox for reconnecting")
    void recallDisconnectedWithdrawsAccess() throws Exception {
        connectDirect("refresh-token-1");
        String calendarId = recallCalendarId();
        recall.disconnect(calendarId);
        recall.deliverNext(List.of(calendarId));

        mvc.perform(post("/api/v1/outreach/webhooks/recall").header("webhook-signature", "v1,forged")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        assertThat(connectionRow().get("status")).isEqualTo("ACTIVE");

        mvc.perform(post("/api/v1/outreach/webhooks/recall")
                        .header("webhook-signature", RecordingRecallCalendarApi.VALID_SIGNATURE)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        assertThat(connectionRow().get("status")).isEqualTo("ERROR");
        assertThat(recallCalendarId()).isNull();
        assertThat(recall.deleted()).containsExactly(calendarId);
    }

    @Test
    @DisplayName("a calendar Recall could not make at connect is made by the next poll; the connect still succeeds")
    void aFailedCreateIsMadeByThePoll() throws Exception {
        recall.failCreates(true);
        connectDirect("refresh-token-1");
        assertThat(connectionRow().get("status")).isEqualTo("ACTIVE");
        assertThat(recallCalendarId()).isNull();

        assertThat(connectionRow().get("recall_calendar_attempts")).isEqualTo(1);

        recall.failCreates(false);
        dispatcher.pollReplies();
        assertThat(recallCalendarId()).as("still backing off").isNull();

        jdbc.update("update app_lm_mailbox_connection set recall_calendar_retry_at = now() - interval '1 minute' "
                + "where workspace_id = ?::uuid", workspaceId);
        dispatcher.pollReplies();
        assertThat(recallCalendarId()).isNotNull();
        assertThat(connectionRow().get("recall_calendar_attempts")).isEqualTo(0);
    }

    @Test
    @DisplayName("a calendar that keeps failing is given up after five tries, until the mailbox reconnects")
    void aCalendarThatKeepsFailingIsGivenUp() throws Exception {
        recall.failCreates(true);
        connectDirect("refresh-token-1");
        for (int attempt = 0; attempt < 6; attempt++) {
            jdbc.update("update app_lm_mailbox_connection set recall_calendar_retry_at = now() - interval '1 minute' "
                    + "where workspace_id = ?::uuid", workspaceId);
            dispatcher.pollReplies();
        }
        assertThat(connectionRow().get("recall_calendar_attempts")).isEqualTo(5);

        recall.failCreates(false);
        connectDirect("refresh-token-2");
        assertThat(recallCalendarId()).isNotNull();
    }

    @Test
    @DisplayName("a reconnect at another host or address gets a new calendar; the old one is deleted, not patched")
    void aReconnectElsewhereReplacesTheCalendar() throws Exception {
        connectDirect("refresh-token-1");
        String gmail = recallCalendarId();

        gateway.grant(new GrantedMailbox(MailboxGrants.mintDirect("microsoft"), ADDRESS, "microsoft",
                "refresh-token-2"));
        connect();

        assertThat(recall.updates()).isEmpty();
        assertThat(recall.deleted()).containsExactly(gmail);
        assertThat(recall.createdSpecs()).hasSize(2);
        assertThat(recall.createdSpecs().get(1).platform()).isEqualTo("microsoft_outlook");
        assertThat(recall.createdSpecs().get(1).clientId()).isEqualTo("uncava-microsoft-client");
        String outlook = recallCalendarId();
        assertThat(outlook).isNotNull().isNotEqualTo(gmail);

        gateway.grant(new GrantedMailbox(MailboxGrants.mintDirect("microsoft"), "other@firm.example", "microsoft",
                "refresh-token-3"));
        connect();
        assertThat(recall.updates()).isEmpty();
        assertThat(recall.deleted()).containsExactly(gmail, outlook);
    }

    @Test
    @DisplayName("a Nylas grant holds no token of ours and gets no Recall calendar")
    void nylasGrantsAreLeftAlone() throws Exception {
        gateway.grant(new GrantedMailbox("grant-nylas-" + domain, ADDRESS, "google"));
        connect();

        Map<String, Object> row = connectionRow();
        assertThat(row.get("gateway")).isEqualTo("NYLAS");
        assertThat(row.get("refresh_token_encrypted")).isNull();
        assertThat(recall.createdSpecs()).isEmpty();
    }

    private void connectDirect(String refreshToken) throws Exception {
        gateway.grant(new GrantedMailbox(MailboxGrants.mintDirect("google"), ADDRESS, "google", refreshToken));
        connect();
    }

    private void connect() throws Exception {
        MvcResult started = mvc.perform(post(MAILBOX + "/connect").header("Authorization", "Bearer " + consultant)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"google\"}"))
                .andExpect(status().isOk())
                .andReturn();
        URI authorization = URI.create(body(started).get("authorizationUrl").asText());
        String state = UriComponentsBuilder.fromUri(authorization).build().getQueryParams().getFirst("state");
        Cookie cookie = started.getResponse().getCookie("lm_mailbox_connect");
        mvc.perform(get(MAILBOX + "/callback").param("state", state).param("code", "code-1").cookie(cookie))
                .andExpect(status().isFound());
    }

    private void switchCalendarSync(String choice) throws Exception {
        mvc.perform(put("/api/v1/workspace/calendar-sync").header("Authorization", "Bearer " + consultant)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"calendarSync\":\"%s\"}".formatted(choice)))
                .andExpect(status().isOk());
    }

    private Map<String, Object> connectionRow() {
        return jdbc.queryForMap("select gateway, status, refresh_token_encrypted, recall_calendar_id, "
                + "recall_calendar_attempts "
                + "from app_lm_mailbox_connection where workspace_id = ?::uuid", workspaceId);
    }

    private String recallCalendarId() {
        return (String) connectionRow().get("recall_calendar_id");
    }
}
