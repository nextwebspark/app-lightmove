package app.lightmove.api.outreach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingMailboxGateway;
import app.lightmove.api.outreach.service.OutreachDispatcher;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * A deployment that connects new mailboxes through our own gateway, holding a mailbox Nylas connected before it
 * did: the mailbox read offers the move, and counts the runs it would stop.
 */
@IntegrationTest
@TestPropertySource(properties = "lightmove.outreach.gateway=direct")
class MoveOffNylasIntegrationTest extends FlowTestSupport {

    private static final ZoneId DUBAI = ZoneId.of("Asia/Dubai");
    private static final String MAILBOX = "consultant@firm.example";

    @Autowired private RecordingMailboxGateway gateway;
    @Autowired private OutreachDispatcher dispatcher;
    @Autowired private JdbcTemplate jdbc;

    private String consultant;
    private String consultantEmail;
    private String projectId;

    @BeforeEach
    void setUp() throws Exception {
        gateway.clear();
        jdbc.update("update app_lm_outreach_enrollment set status = 'STOPPED', stop_reason = 'MANUAL', "
                + "next_send_at = null, sending_since = null where status in ('SCHEDULED', 'ACTIVE')");
        consultantEmail = "yara@" + domain;
        createWorkspace(verifiedUser("Yara Haddad", consultantEmail), "Meridian Search");
        consultant = login(consultantEmail);
        jdbc.update("""
                insert into app_lm_mailbox_connection (workspace_id, user_id, provider, address, grant_id, status,
                                                       daily_cap, connected_at, gateway)
                select m.workspace_id, u.id, 'google', ?, ?, 'ACTIVE', 50, now(), 'NYLAS'
                from app_lm_user u join app_lm_workspace_member m on m.user_id = u.id where u.email = ?
                """, MAILBOX, "nylas-" + UUID.randomUUID(), consultantEmail);
        projectId = mandate();
    }

    /** The reply poll sweeps every mailbox in the shared database, and a seeded row's token is not a real seal. */
    @AfterEach
    void forgetSeededMailbox() {
        jdbc.update("delete from app_lm_mailbox_connection where user_id = (select id from app_lm_user where email = ?)",
                consultantEmail);
    }

    @Test
    @DisplayName("a Nylas mailbox is offered the move, with the runs still sending in its threads counted")
    void aNylasMailboxIsOfferedTheMove() throws Exception {
        String sequenceId = createSequence();
        start(sequenceId, executive("Priya Raman", "priya@" + domain), "priya@" + domain);
        Instant monday = LocalDate.now(DUBAI).plusWeeks(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                .atTime(LocalTime.of(10, 0)).atZone(DUBAI).toInstant();
        dispatcher.dispatchAt(monday);
        start(sequenceId, executive("Rajesh Iyer", "rajesh@" + domain), "rajesh@" + domain);

        as(consultant, get("/api/v1/outreach/mailbox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connection.movesOffNylas").value(true))
                .andExpect(jsonPath("$.connection.runsStoppedByMove").value(1));
    }

    @Test
    @DisplayName("a mailbox already on our own gateway is offered nothing")
    void aDirectMailboxIsOfferedNothing() throws Exception {
        jdbc.update("update app_lm_mailbox_connection set gateway = 'DIRECT', grant_id = ?, "
                + "refresh_token_encrypted = 'sealed' where user_id = (select id from app_lm_user where email = ?)",
                "direct:google:" + UUID.randomUUID(), consultantEmail);

        as(consultant, get("/api/v1/outreach/mailbox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connection.movesOffNylas").value(false))
                .andExpect(jsonPath("$.connection.runsStoppedByMove").value(0));
    }

    private void start(String sequenceId, String candidateId, String address) throws Exception {
        as(consultant, post(outreach("/sequences/" + sequenceId + "/enrollments"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"people":[{"candidateId":"%s","toAddress":"%s","opener":null,"openerEdited":false}]}"""
                        .formatted(candidateId, address)))
                .andExpect(status().isCreated());
    }

    private String createSequence() throws Exception {
        return body(as(consultant, post(outreach("/sequences")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"First approach","steps":[
                          {"delayWorkingDays":0,"subject":"Confidential: {{positionTitle}}","body":"Hi {{firstName}}."},
                          {"delayWorkingDays":3,"body":"Following up, {{firstName}}."}]}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private String executive(String fullName, String emailAddress) throws Exception {
        String slug = fullName.toLowerCase().replace(' ', '-') + "-" + System.nanoTime();
        return body(as(consultant, post("/api/v1/projects/" + projectId + "/candidates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"%s","title":"Chief Financial Officer","employerName":"Target Group",
                         "linkedinUrl":"https://www.linkedin.com/in/%s","email":"%s"}
                        """.formatted(fullName, slug, emailAddress)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
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

    private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private String outreach(String path) {
        return "/api/v1/projects/" + projectId + "/outreach" + path;
    }
}
