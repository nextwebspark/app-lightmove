package app.lightmove.api.notetaker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingMailboxGateway;
import app.lightmove.api.RecordingMeetingRecorder;
import app.lightmove.api.billing.usage.constant.UsageKind;
import app.lightmove.api.billing.usage.model.MeteredUse;
import app.lightmove.api.billing.usage.service.UsageRecorder;
import app.lightmove.api.notetaker.service.NotetakerScheduler;
import app.lightmove.api.outreach.model.CalendarEvent;
import app.lightmove.api.outreach.model.CalendarEventChanged;
import app.lightmove.api.outreach.model.GrantedMailbox;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

/**
 * #956: recorded minutes are a fair-use kind per seat, asked before a bot is sent, and Settings → Billing reads them.
 */
@IntegrationTest
@TestPropertySource(properties = "lightmove.billing.fair-use.meeting-minute-per-seat=30")
class NotetakerFairUseIntegrationTest extends FlowTestSupport {

    private static final String MAILBOX = "consultant@firm.example";

    @Autowired private RecordingMailboxGateway gateway;
    @Autowired private RecordingMeetingRecorder recorder;
    @Autowired private NotetakerScheduler scheduler;
    @Autowired private UsageRecorder usage;
    @Autowired private JdbcTemplate jdbc;

    private String grantId;
    private String consultant;
    private String projectId;
    private String workspaceId;
    private int calls;

    @BeforeEach
    void setUp() throws Exception {
        gateway.clear();
        recorder.clear();
        grantId = "grant-" + domain;
        gateway.grant(new GrantedMailbox(grantId, MAILBOX, "google"));
        String email = "yara@" + domain;
        createWorkspace(verifiedUser("Yara Haddad", email), "Fair Use Notes " + domain);
        consultant = login(email);
        projectId = mandate();
        workspaceId = jdbc.queryForObject("select workspace_id::text from app_lm_project where id = ?::uuid",
                String.class, projectId);
        connectMailbox();
    }

    @Test
    @DisplayName("with minutes left a bot is sent; with the month's minutes used the call is skipped, saying why")
    void minutesAreAskedBeforeABotIsSent() throws Exception {
        UUID userId = jdbc.queryForObject("select id from app_lm_user where email = ?", UUID.class, "yara@" + domain);
        usage.record(MeteredUse.of(UUID.fromString(workspaceId), userId, UUID.fromString(projectId),
                UsageKind.MEETING_MINUTES, 29));
        sendBot();
        assertThat(recorder.launched()).hasSize(1);

        JsonNode billing = body(as(consultant, get("/api/v1/billing/usage")).andExpect(status().isOk()).andReturn());
        assertThat(billing.get("notetakerMinutes").get("used").asLong()).isEqualTo(29);
        assertThat(billing.get("notetakerMinutes").get("ceiling").asLong()).isEqualTo(30);

        usage.record(MeteredUse.of(UUID.fromString(workspaceId), userId, UUID.fromString(projectId),
                UsageKind.MEETING_MINUTES, 1));
        recorder.clear();
        sendBot();

        assertThat(recorder.launched()).isEmpty();
        Map<String, Object> skipped = jdbc.queryForMap("select status, end_reason from app_lm_meeting_recording "
                + "where workspace_id = ?::uuid order by created_at desc limit 1", workspaceId);
        assertThat(skipped).containsEntry("status", "SKIPPED").containsEntry("end_reason", "FAIR_USE_REACHED");
    }

    private void sendBot() throws Exception {
        String id = "call-" + System.nanoTime();
        executive(calls++ == 0 ? "Priya Raman" : "Rajesh Menon",
                "priya-" + id + "@" + domain);
        Instant start = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(Duration.ofMinutes(1));
        gateway.deliverNext(List.of(new CalendarEventChanged(grantId, new CalendarEvent(id, "Conversation",
                start, start.plus(Duration.ofMinutes(30)), List.of("priya-" + id + "@" + domain, MAILBOX),
                "https://meet.google.com/" + id, "Google Meet"))));
        mvc.perform(post("/api/v1/outreach/webhooks/mailbox")
                        .header("X-Nylas-Signature", RecordingMailboxGateway.VALID_SIGNATURE)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        scheduler.scheduleAt(Instant.now());
    }

    private void executive(String fullName, String emailAddress) throws Exception {
        as(consultant, post("/api/v1/projects/" + projectId + "/candidates").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"%s","title":"Chief Financial Officer","employerName":"Target Group",
                         "linkedinUrl":"https://www.linkedin.com/in/priya-%d","email":"%s"}
                        """.formatted(fullName, System.nanoTime(), emailAddress)))
                .andExpect(status().isCreated());
    }

    private String mandate() throws Exception {
        String clientId = body(as(consultant, post("/api/v1/clients").contentType(MediaType.APPLICATION_JSON)
                .content("{\"customName\":\"Acme Holdings\"}"))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        return body(as(consultant, post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"%s\",\"positionTitle\":\"Group CFO\"}".formatted(clientId)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private void connectMailbox() throws Exception {
        MvcResult started = as(consultant, post("/api/v1/outreach/mailbox/connect")
                .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"google\"}"))
                .andExpect(status().isOk())
                .andReturn();
        URI authorization = URI.create(body(started).get("authorizationUrl").asText());
        String state = UriComponentsBuilder.fromUri(authorization).build().getQueryParams().getFirst("state");
        Cookie cookie = started.getResponse().getCookie("lm_mailbox_connect");
        mvc.perform(get("/api/v1/outreach/mailbox/callback").param("state", state).param("code", "code-1")
                        .cookie(cookie))
                .andExpect(status().isFound());
    }

    private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }
}
