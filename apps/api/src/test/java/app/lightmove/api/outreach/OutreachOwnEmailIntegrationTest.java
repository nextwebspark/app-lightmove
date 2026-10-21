package app.lightmove.api.outreach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingMailboxGateway;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.outreach.model.GrantedMailbox;
import app.lightmove.api.outreach.model.OutgoingEmail;
import app.lightmove.api.outreach.service.OutreachDispatcher;
import app.lightmove.api.outreach.service.OutreachInboxService;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

/**
 * Phase 3 of docs/outreach-conversations.md: a consultant's own email to one executive — a first email or a reply
 * in their thread — checked when written, held as a scheduled line, sent by the dispatcher once and never again,
 * and what that send means for the person, their reply task and a sequence still running in the thread.
 */
@IntegrationTest
class OutreachOwnEmailIntegrationTest extends FlowTestSupport {

    private static final String MAILBOX = "consultant@firm.example";

    @Autowired private RecordingMailboxGateway gateway;
    @Autowired private OutreachDispatcher dispatcher;
    @Autowired private OutreachInboxService inbox;
    @Autowired private JdbcTemplate jdbc;

    private String consultant;
    private String projectId;
    private String priya;
    private String priyaAddress;

    @BeforeEach
    void setUp() throws Exception {
        gateway.clear();
        // The suite shares one database: anything other classes left due would otherwise go out in these passes.
        jdbc.update("update app_lm_outreach_enrollment set status = 'STOPPED', stop_reason = 'MANUAL', "
                + "next_send_at = null, sending_since = null where status in ('SCHEDULED', 'ACTIVE')");
        jdbc.update("update app_lm_outreach_message set status = 'CANCELLED', sending_since = null "
                + "where status = 'SCHEDULED'");
        String consultantEmail = "yara@" + domain;
        // Its own name: the suite shares one database, and a name runs out of free slugs after 99 workspaces.
        createWorkspace(verifiedUser("Yara Haddad", consultantEmail), "Own Email Partners");
        consultant = login(consultantEmail);
        String clientId = body(as(consultant, post("/api/v1/clients").contentType(MediaType.APPLICATION_JSON)
                .content("{\"customName\":\"Acme Holdings\"}")).andExpect(status().isCreated()).andReturn())
                .get("id").asText();
        projectId = body(as(consultant, post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"%s\",\"positionTitle\":\"Group CFO\"}".formatted(clientId)))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        gateway.grant(new GrantedMailbox("grant-" + domain, MAILBOX, "google"));
        connectMailbox();
        priyaAddress = "priya@" + domain;
        priya = executive("Priya Raman", priyaAddress);
    }

    @Test
    @DisplayName("a first email goes once, as Markdown rendered safe, and moves the person to Contacted")
    void aFirstEmailGoesOnce() throws Exception {
        JsonNode scheduled = send("""
                {"toAddress":"%s","subject":"A conversation","replyInThread":false,
                 "body":"Hi {{firstName}},\\n\\n**Group CFO** <script>x</script> [brief](javascript:alert(1))"}"""
                .formatted(priyaAddress));
        assertThat(scheduled.get("status").asText()).isEqualTo("SCHEDULED");
        assertThat(gateway.sent()).isEmpty();

        Instant now = Instant.now().plusSeconds(5);
        dispatcher.dispatchAt(now);
        dispatcher.dispatchAt(now.plusSeconds(60));

        List<RecordingMailboxGateway.SentRecord> sent = gateway.sent();
        assertThat(sent).hasSize(1);
        OutgoingEmail email = sent.getFirst().email();
        assertThat(email.to()).isEqualTo(priyaAddress);
        assertThat(email.subject()).isEqualTo("A conversation");
        assertThat(email.replyToMessageId()).isNull();
        assertThat(email.htmlBody()).contains("Hi Priya,", "<strong>Group CFO</strong>", "&lt;script&gt;")
                .doesNotContain("<script>", "javascript:");
        Map<String, Object> line = line(scheduled.get("id").asText());
        assertThat(line).containsEntry("status", "SENT").containsEntry("kind", "MANUAL")
                .containsEntry("body_format", "MARKDOWN");
        assertThat(line.get("sent_at")).isNotNull();
        assertThat(line.get("thread_id")).isNotNull();
        assertThat(candidateStatus()).isEqualTo("contacted");
        assertThat(activityKinds()).contains("EMAIL_SENT");
    }

    @Test
    @DisplayName("a reply in the thread answers the executive's own message, closes the reply task, keeps the subject")
    void aReplyAnswersTheThread() throws Exception {
        start(createSequence(), priyaAddress);
        Instant monday = nextMondayTen();
        dispatcher.dispatchAt(monday);
        String thread = (String) enrollment().get("thread_id");
        String theirs = gateway.writeInto(thread, priyaAddress, "Happy to talk.");
        inbox.pollListeningThreads(monday.plus(Duration.ofMinutes(15)));
        assertThat(openReplyTasks()).isEqualTo(1);

        JsonNode scheduled = send("""
                {"toAddress":"%s","replyInThread":true,"body":"Thursday at 10?"}""".formatted(priyaAddress));
        dispatcher.dispatchAt(Instant.now().plusSeconds(5));

        OutgoingEmail email = gateway.sent().getLast().email();
        assertThat(email.replyToMessageId()).isEqualTo(theirs);
        assertThat(email.subject()).isEqualTo("Re: Confidential: Group CFO");
        assertThat(line(scheduled.get("id").asText())).containsEntry("thread_id", thread);
        assertThat(openReplyTasks()).isZero();
    }

    @Test
    @DisplayName("an email into a thread a sequence is still running stops the sequence")
    void anOwnEmailTakesTheThreadOver() throws Exception {
        start(createSequence(), priyaAddress);
        dispatcher.dispatchAt(nextMondayTen());
        assertThat(enrollment().get("status")).isEqualTo("ACTIVE");

        send("""
                {"toAddress":"%s","replyInThread":true,"body":"A word before my colleague writes again."}"""
                .formatted(priyaAddress));
        dispatcher.dispatchAt(Instant.now().plusSeconds(5));

        assertThat(enrollment()).containsEntry("status", "STOPPED").containsEntry("stop_reason", "MANUAL");
    }

    @Test
    @DisplayName("a scheduled email waits for its time, can be cancelled until it is taken, and never goes after")
    void aScheduledEmailCanBeCancelled() throws Exception {
        Instant later = Instant.now().plus(Duration.ofHours(2));
        String id = send("""
                {"toAddress":"%s","subject":"Later","replyInThread":false,"body":"Hello","sendAt":"%s"}"""
                .formatted(priyaAddress, later)).get("id").asText();

        dispatcher.dispatchAt(Instant.now().plusSeconds(5));
        assertThat(gateway.sent()).isEmpty();
        as(consultant, delete(outreach("/messages/" + id))).andExpect(status().isNoContent());
        dispatcher.dispatchAt(later.plusSeconds(60));

        assertThat(gateway.sent()).isEmpty();
        assertThat(line(id)).containsEntry("status", "CANCELLED");
        as(consultant, delete(outreach("/messages/" + id))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUTREACH_MESSAGE_NOT_SCHEDULED"));
    }

    @Test
    @DisplayName("at the day's cap Send now is refused, and a scheduled email waits for the next morning")
    void theDailyCapHolds() throws Exception {
        jdbc.update("update app_lm_mailbox_connection set daily_cap = 1 where grant_id = ?", "grant-" + domain);
        send("""
                {"toAddress":"%s","subject":"First","replyInThread":false,"body":"Hello"}""".formatted(priyaAddress));
        dispatcher.dispatchAt(Instant.now().plusSeconds(5));
        assertThat(gateway.sent()).hasSize(1);

        sendExpecting(status().isConflict(), """
                {"toAddress":"%s","subject":"Now","replyInThread":false,"body":"Hello"}""".formatted(priyaAddress))
                .andExpect(jsonPath("$.code").value("OUTREACH_DAILY_CAP_REACHED"));
        Instant soon = Instant.now().plus(Duration.ofMinutes(5));
        String id = send("""
                {"toAddress":"%s","subject":"Soon","replyInThread":false,"body":"Hello","sendAt":"%s"}"""
                .formatted(priyaAddress, soon)).get("id").asText();
        dispatcher.dispatchAt(soon.plusSeconds(1));

        assertThat(gateway.sent()).hasSize(1);
        Map<String, Object> line = line(id);
        assertThat(line).containsEntry("status", "SCHEDULED");
        assertThat(((Timestamp) line.get("send_at")).toInstant()).isAfter(soon.plus(Duration.ofHours(1)));
    }

    @Test
    @DisplayName("what the composer refuses: do not contact, an address off the record, a reply with no thread, "
            + "a first email with no subject, a client seat")
    void theComposerRefuses() throws Exception {
        sendExpecting(status().isConflict(), """
                {"toAddress":"someone@else.example","subject":"Hi","replyInThread":false,"body":"Hello"}""")
                .andExpect(jsonPath("$.code").value("OUTREACH_ADDRESS_NOT_ON_RECORD"));
        sendExpecting(status().isConflict(), """
                {"toAddress":"%s","replyInThread":true,"body":"Hello"}""".formatted(priyaAddress))
                .andExpect(jsonPath("$.code").value("OUTREACH_THREAD_UNAVAILABLE"));
        sendExpecting(status().isBadRequest(), """
                {"toAddress":"%s","subject":" ","replyInThread":false,"body":"Hello"}""".formatted(priyaAddress));
        markDoNotContact();
        sendExpecting(status().isConflict(), """
                {"toAddress":"%s","subject":"Hi","replyInThread":false,"body":"Hello"}""".formatted(priyaAddress))
                .andExpect(jsonPath("$.code").value("OUTREACH_DO_NOT_CONTACT"));
    }

    @Test
    @DisplayName("marked do not contact while it waited, the email fails unsent and the queue asks for attention")
    void doNotContactAtSendFailsIt() throws Exception {
        String id = send("""
                {"toAddress":"%s","subject":"Hi","replyInThread":false,"body":"Hello"}""".formatted(priyaAddress))
                .get("id").asText();
        markDoNotContact();

        dispatcher.dispatchAt(Instant.now().plusSeconds(5));

        assertThat(gateway.sent()).isEmpty();
        assertThat(line(id)).containsEntry("status", "FAILED").containsEntry("fail_reason", "DO_NOT_CONTACT");
        JsonNode queue = body(as(consultant, get(outreach("/conversations")).param("view", "needs"))
                .andExpect(status().isOk()).andReturn());
        assertThat(queue.at("/rows/0/group").asText()).isEqualTo("ATTENTION");
    }

    @Test
    @DisplayName("a send whose outcome is unknown fails and is never sent again")
    void anUncertainSendIsNeverRetried() throws Exception {
        String id = send("""
                {"toAddress":"%s","subject":"Hi","replyInThread":false,"body":"Hello"}""".formatted(priyaAddress))
                .get("id").asText();
        gateway.failSendsWith(new VendorException(VendorCall.of("nylas", "send"), VendorFailureKind.TIMEOUT, null));

        dispatcher.dispatchAt(Instant.now().plusSeconds(5));
        gateway.failSendsWith(null);
        dispatcher.dispatchAt(Instant.now().plusSeconds(120));

        assertThat(gateway.sent()).as("asked of the mail service once, and never again").isEmpty();
        assertThat(line(id)).containsEntry("status", "FAILED").containsEntry("fail_reason", "SEND_UNCERTAIN");
    }

    @Test
    @DisplayName("archiving the position cancels what waits there")
    void archivingCancelsWhatWaits() throws Exception {
        String id = send("""
                {"toAddress":"%s","subject":"Hi","replyInThread":false,"body":"Hello","sendAt":"%s"}"""
                .formatted(priyaAddress, Instant.now().plus(Duration.ofDays(1)))).get("id").asText();

        as(consultant, post("/api/v1/projects/" + projectId + "/archive").contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmName\":\"Group CFO\"}")).andExpect(status().is2xxSuccessful());

        assertThat(line(id)).containsEntry("status", "CANCELLED");
    }

    private JsonNode send(String json) throws Exception {
        return body(sendExpecting(status().isCreated(), json).andReturn());
    }

    private ResultActions sendExpecting(org.springframework.test.web.servlet.ResultMatcher expected, String json)
            throws Exception {
        return as(consultant, post(outreach("/conversations/" + priya + "/messages"))
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(expected);
    }

    private void markDoNotContact() throws Exception {
        String personId = jdbc.queryForObject("select person_id::text from app_lm_project_candidate where id = ?::uuid",
                String.class, priya);
        as(consultant, put("/api/v1/candidates/" + personId + "/do-not-contact")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"doNotContact\":true,\"reason\":\"Asked not to be approached.\"}"))
                .andExpect(status().is2xxSuccessful());
    }

    private Map<String, Object> line(String id) {
        return jdbc.queryForMap("select * from app_lm_outreach_message where id = ?::uuid", id);
    }

    private Map<String, Object> enrollment() {
        return jdbc.queryForMap("select * from app_lm_outreach_enrollment where candidate_id = ?::uuid", priya);
    }

    private int openReplyTasks() {
        return jdbc.queryForObject("select count(*) from app_lm_task t join app_lm_project_candidate c "
                + "on c.person_id = t.person_id and c.project_id = t.project_id where c.id = ?::uuid "
                + "and t.rule = 'REPLY' and t.status = 'OPEN'", Integer.class, priya);
    }

    private List<String> activityKinds() {
        return jdbc.queryForList("select a.kind from app_lm_person_activity a join app_lm_project_candidate c "
                + "on c.person_id = a.person_id where c.id = ?::uuid order by a.occurred_at, a.id", String.class, priya);
    }

    private String candidateStatus() throws Exception {
        return body(as(consultant, get("/api/v1/projects/" + projectId + "/candidates/" + priya)).andReturn())
                .get("status").asText();
    }

    private static Instant nextMondayTen() {
        java.time.ZoneId dubai = java.time.ZoneId.of("Asia/Dubai");
        return java.time.LocalDate.now(dubai).plusWeeks(1)
                .with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY))
                .atTime(java.time.LocalTime.of(10, 0)).atZone(dubai).toInstant();
    }

    private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private String outreach(String path) {
        return "/api/v1/projects/" + projectId + "/outreach" + path;
    }

    private void start(String sequenceId, String toAddress) throws Exception {
        as(consultant, post(outreach("/sequences/" + sequenceId + "/enrollments"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"people":[{"candidateId":"%s","toAddress":"%s","opener":null,"openerEdited":false}]}"""
                        .formatted(priya, toAddress)))
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
        String slug = fullName.toLowerCase().replace(' ', '-') + "-" + emailAddress.hashCode();
        return body(as(consultant, post("/api/v1/projects/" + projectId + "/candidates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"%s","title":"Chief Financial Officer","employerName":"Target Group",
                         "linkedinUrl":"https://www.linkedin.com/in/%s","email":"%s"}
                        """.formatted(fullName, slug, emailAddress)))
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
}
