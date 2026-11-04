package app.lightmove.api.outreach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingMailboxGateway;
import app.lightmove.api.outreach.model.DeliveryFailure;
import app.lightmove.api.outreach.model.GrantedMailbox;
import app.lightmove.api.outreach.model.InboundMessage;
import app.lightmove.api.outreach.model.MailboxEvent;
import app.lightmove.api.outreach.model.ThreadMessage;
import app.lightmove.api.outreach.service.OutreachDispatcher;
import app.lightmove.api.outreach.service.OutreachInboxService;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
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
 * Phase 2 of docs/outreach-conversations.md: a reply or a bounce is a line of the conversation, found by the webhook
 * or the poll once, its words kept where the mailbox grants them, and what it means for the run and its task decided
 * the same way whichever found it. The clock is the test's, as in {@code OutreachDispatchIntegrationTest}.
 *
 * <p>The mailbox is the recording gateway's Nylas grant, so the read is the Nylas app's: this class is the one
 * context that says its scopes reach a body. A direct grant would be answered by the provider's own gateway.
 */
@IntegrationTest
@TestPropertySource(properties = "lightmove.outreach.nylas.reads-replies=true")
class OutreachReplyLedgerIntegrationTest extends FlowTestSupport {

    private static final ZoneId DUBAI = ZoneId.of("Asia/Dubai");
    private static final String MAILBOX = "consultant@firm.example";

    @Autowired private RecordingMailboxGateway gateway;
    @Autowired private OutreachDispatcher dispatcher;
    @Autowired private OutreachInboxService inbox;
    @Autowired @FixtureJdbc private JdbcTemplate jdbc;

    private String consultant;
    private String projectId;
    private String priya;
    private String thread;
    private Instant monday;

    @BeforeEach
    void setUp() throws Exception {
        gateway.clear();
        // The suite shares one database: runs other classes left due would otherwise go out in these passes.
        jdbc.update("update app_lm_outreach_enrollment set status = 'STOPPED', stop_reason = 'MANUAL', "
                + "next_send_at = null, sending_since = null where status in ('SCHEDULED', 'ACTIVE')");
        String consultantEmail = "yara@" + domain;
        // Its own name: the suite shares one database, and a name runs out of free slugs after 99 workspaces.
        createWorkspace(verifiedUser("Yara Haddad", consultantEmail), "Reply Ledger Partners");
        consultant = login(consultantEmail);
        String clientId = body(as(consultant, post("/api/v1/clients").contentType(MediaType.APPLICATION_JSON)
                .content("{\"customName\":\"Acme Holdings\"}")).andExpect(status().isCreated()).andReturn())
                .get("id").asText();
        projectId = body(as(consultant, post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"%s\",\"positionTitle\":\"Group CFO\"}".formatted(clientId)))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        // A grant of its own: the poll reads every recent thread in the shared database, and older tests' threads
        // carry the same ids under the default grant.
        gateway.grant(new GrantedMailbox("grant-" + domain, MAILBOX, "google"));
        connectMailbox();
        monday = LocalDate.now(DUBAI).plusWeeks(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                .atTime(LocalTime.of(10, 0)).atZone(DUBAI).toInstant();
        priya = executive("Priya Raman", "priya@" + domain);
        start(createSequence(), priya, "priya@" + domain);
        dispatcher.dispatchAt(monday);
        thread = (String) enrollmentOf(priya).get("thread_id");
    }

    @Test
    @DisplayName("a reply the poll finds is a line with its words and not the history it quoted, and ends the run")
    void aPolledReplyKeepsItsWords() throws Exception {
        gateway.writeInto(thread, "Priya@" + domain,
                "Thursday works.\n\nOn Mon, Yara Haddad <" + MAILBOX + "> wrote:\n> Would you be open to a call?");

        inbox.pollListeningThreads(monday.plus(Duration.ofMinutes(15)));

        assertThat(replies()).extracting(row -> row.get("body_text"), row -> row.get("from_address"))
                .containsExactly(tuple("Thursday works.", "priya@" + domain));
        assertThat(enrollmentOf(priya).get("status")).isEqualTo("REPLIED");
        assertThat(openReplyTasks()).isEqualTo(1);

        JsonNode conversation = body(as(consultant, get(outreach("/conversations/" + priya)))
                .andExpect(status().isOk()).andReturn());
        JsonNode reply = null;
        for (JsonNode event : conversation.get("events")) {
            if (event.at("/message/kind").asText("").equals("REPLY")) {
                reply = event.get("message");
            }
        }
        assertThat(reply).isNotNull();
        assertThat(reply.get("text").asText()).isEqualTo("Thursday works.");
        JsonNode queue = body(as(consultant, get(outreach("/conversations")).param("view", "all"))
                .andExpect(status().isOk()).andReturn());
        assertThat(queue.at("/rows/0/latest/preview").asText()).isEqualTo("Thursday works.");
        JsonNode drawer = body(as(consultant, get(outreach("/candidates/" + priya))).andExpect(status().isOk())
                .andReturn());
        assertThat(drawer.at("/latestReply/text").asText()).isEqualTo("Thursday works.");
        assertThat(drawer.at("/latestReply/at").isNull()).isFalse();
    }

    @Test
    @DisplayName("a reply the webhook reports and the poll finds again is one line, its outcome written once")
    void theWebhookAndThePollRecordOnce() throws Exception {
        Instant sent = monday.plus(Duration.ofMinutes(5));
        webhook(List.of(new InboundMessage("grant-" + domain, thread, "priya@" + domain, "msg-from-priya", sent,
                "<div>Yes, call me.</div><div class=\"gmail_quote\"><div class=\"gmail_attr\">On Mon, Yara wrote:</div>"
                + "<blockquote class=\"gmail_quote\">Hi</blockquote></div>")));
        gateway.writeInto(thread, new ThreadMessage("msg-from-priya", "priya@" + domain, sent, "Yes, call me."));
        inbox.pollListeningThreads(monday.plus(Duration.ofMinutes(15)));
        webhook(List.of(new InboundMessage("grant-" + domain, thread, "priya@" + domain, "msg-from-priya", sent, null)));

        assertThat(replies()).extracting(row -> row.get("body_text")).containsExactly("Yes, call me.");
        assertThat(activityKinds(priya).stream().filter("EMAIL_REPLIED"::equals)).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from app_lm_audit_event where event_type = 'OUTREACH_REPLIED' "
                + "and metadata ->> 'enrollmentId' = ?", Integer.class, enrollmentOf(priya).get("id").toString()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a truncated delivery is read back from the thread for its words")
    void aTruncatedDeliveryIsReadBack() throws Exception {
        Instant sent = monday.plus(Duration.ofMinutes(5));
        gateway.writeInto(thread, new ThreadMessage("msg-long", "priya@" + domain, sent, "A long answer."));

        webhook(List.of(new InboundMessage("grant-" + domain, thread, "priya@" + domain, "msg-long", sent, null)));

        assertThat(replies()).extracting(row -> row.get("body_text")).containsExactly("A long answer.");
    }

    @Test
    @DisplayName("a second reply after the run ended is its own line, and files no second task while one is open")
    void aSecondReplyAddsALineNotATask() throws Exception {
        gateway.writeInto(thread, "priya@" + domain, "Thursday works.");
        inbox.pollListeningThreads(monday.plus(Duration.ofMinutes(15)));
        gateway.writeInto(thread, "priya@" + domain, "Actually, Friday is better.");
        inbox.pollListeningThreads(monday.plus(Duration.ofMinutes(30)));

        assertThat(replies()).extracting(row -> row.get("body_text"))
                .containsExactly("Thursday works.", "Actually, Friday is better.");
        assertThat(enrollmentOf(priya).get("status")).isEqualTo("REPLIED");
        assertThat(activityKinds(priya).stream().filter("EMAIL_REPLIED"::equals)).hasSize(2);
        assertThat(openReplyTasks()).isEqualTo(1);
    }

    @Test
    @DisplayName("a reply to a run long finished, first found now, is kept as a line and announced to nobody")
    void anOldReplyToAFinishedRunIsHistory() throws Exception {
        jdbc.update("update app_lm_outreach_enrollment set status = 'STOPPED', stop_reason = 'MANUAL', "
                + "stopped_at = ?, next_send_at = null where id = ?", Timestamp.from(monday.plus(Duration.ofHours(1))),
                enrollmentOf(priya).get("id"));
        gateway.writeInto(thread, "priya@" + domain, "Sorry for the slow answer.", monday.plus(Duration.ofHours(2)));

        inbox.pollListeningThreads(monday.plus(Duration.ofDays(5)));

        assertThat(replies()).extracting(row -> row.get("body_text")).containsExactly("Sorry for the slow answer.");
        assertThat(activityKinds(priya)).doesNotContain("EMAIL_REPLIED");
        assertThat(openReplyTasks()).isZero();
        assertThat(enrollmentOf(priya).get("status")).isEqualTo("STOPPED");
    }

    @Test
    @DisplayName("a run still waiting is ended by its reply however late the reply is found")
    void aLateReplyStillEndsAWaitingRun() throws Exception {
        gateway.writeInto(thread, "priya@" + domain, "Yes.", monday.plus(Duration.ofMinutes(5)));

        inbox.pollListeningThreads(monday.plus(Duration.ofDays(2)));

        assertThat(enrollmentOf(priya).get("status")).isEqualTo("REPLIED");
        assertThat(openReplyTasks()).isEqualTo(1);
    }

    @Test
    @DisplayName("a thread sent from another address is not read once the mailbox is connected to another account")
    void anotherAccountsThreadIsNotRead() throws Exception {
        jdbc.update("update app_lm_mailbox_connection set address = ? where grant_id = ?", "yara.new@" + domain,
                "grant-" + domain);
        gateway.writeInto(thread, "priya@" + domain, "Thursday works.");

        inbox.pollListeningThreads(monday.plus(Duration.ofMinutes(15)));

        assertThat(replies()).isEmpty();
    }

    @Test
    @DisplayName("a placeholder V145 left for an earlier reply is completed by the message, not joined by it")
    void aPlaceholderIsCompleted() throws Exception {
        Instant noticed = monday.plus(Duration.ofMinutes(20));
        Map<String, Object> run = enrollmentOf(priya);
        jdbc.update("update app_lm_outreach_enrollment set status = 'REPLIED', replied_at = ? where id = ?",
                Timestamp.from(noticed), run.get("id"));
        jdbc.update("""
                insert into app_lm_outreach_message (workspace_id, project_id, person_id, candidate_id, enrollment_id,
                    direction, kind, status, sender_user_id, from_address, thread_id, thread_gateway, occurred_at)
                select workspace_id, project_id, person_id, candidate_id, id, 'INBOUND', 'REPLY', 'RECEIVED',
                    sender_user_id, to_address, thread_id, thread_gateway, replied_at
                from app_lm_outreach_enrollment where id = ?""", run.get("id"));
        gateway.writeInto(thread, "priya@" + domain, "Thursday works.", monday.plus(Duration.ofMinutes(10)));

        inbox.pollListeningThreads(monday.plus(Duration.ofMinutes(30)));

        List<Map<String, Object>> replies = replies();
        assertThat(replies).hasSize(1);
        assertThat(replies.getFirst().get("body_text")).isEqualTo("Thursday works.");
        assertThat(replies.getFirst().get("provider_message_id")).isNotNull();
        assertThat(((Timestamp) replies.getFirst().get("occurred_at")).toInstant())
                .isEqualTo(monday.plus(Duration.ofMinutes(10)));
        assertThat(activityKinds(priya)).doesNotContain("EMAIL_REPLIED");
    }

    @Test
    @DisplayName("a reported bounce is one line, completed by the mail server's message, however often it is told")
    void aBounceIsOneLine() throws Exception {
        webhook(List.of(new DeliveryFailure("grant-" + domain, thread, null)));
        assertThat(enrollmentOf(priya).get("status")).isEqualTo("BOUNCED");

        // Received before the report came, as a mail server's notice always is.
        gateway.writeInto(thread, "mailer-daemon@googlemail.com", "Address not found");
        inbox.pollListeningThreads(monday.plus(Duration.ofMinutes(15)));
        webhook(List.of(new DeliveryFailure("grant-" + domain, thread, null)));

        List<Map<String, Object>> bounces = jdbc.queryForList("select * from app_lm_outreach_message "
                + "where thread_id = ? and kind = 'BOUNCE' and project_id = ?::uuid", thread, projectId);
        assertThat(bounces).hasSize(1);
        assertThat(bounces.getFirst().get("from_address")).isEqualTo("mailer-daemon@googlemail.com");
        assertThat(bounces.getFirst().get("body_text")).as("a mail server's words quote our own email").isNull();
        assertThat(replies()).isEmpty();
    }

    @Test
    @DisplayName("the mailbox read says the words are kept")
    void theMailboxSaysItReadsReplies() throws Exception {
        JsonNode mailbox = body(as(consultant, get("/api/v1/outreach/mailbox")).andExpect(status().isOk()).andReturn());

        assertThat(mailbox.at("/connection/readsReplies").asBoolean()).isTrue();
        assertThat(mailbox.at("/connection/reconnectToReadReplies").asBoolean()).isFalse();
    }

    private List<Map<String, Object>> replies() {
        return jdbc.queryForList("select * from app_lm_outreach_message where thread_id = ? and kind = 'REPLY' "
                + "and project_id = ?::uuid order by occurred_at", thread, projectId);
    }

    private int openReplyTasks() {
        return jdbc.queryForObject("select count(*) from app_lm_task t join app_lm_project_candidate c "
                + "on c.person_id = t.person_id and c.project_id = t.project_id where c.id = ?::uuid "
                + "and t.rule = 'REPLY' and t.status = 'OPEN'", Integer.class, priya);
    }

    private void webhook(List<MailboxEvent> events) throws Exception {
        gateway.deliverNext(events);
        mvc.perform(post("/api/v1/outreach/webhooks/mailbox")
                        .header("X-Nylas-Signature", RecordingMailboxGateway.VALID_SIGNATURE)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    private Map<String, Object> enrollmentOf(String candidateId) {
        return jdbc.queryForMap("select * from app_lm_outreach_enrollment where candidate_id = ?::uuid", candidateId);
    }

    private List<String> activityKinds(String candidateId) {
        return jdbc.queryForList("select a.kind from app_lm_person_activity a join app_lm_project_candidate c "
                + "on c.person_id = a.person_id where c.id = ?::uuid order by a.occurred_at, a.id", String.class,
                candidateId);
    }

    private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private String outreach(String path) {
        return "/api/v1/projects/" + projectId + "/outreach" + path;
    }

    private void start(String sequenceId, String candidateId, String toAddress) throws Exception {
        as(consultant, post(outreach("/sequences/" + sequenceId + "/enrollments"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"people":[{"candidateId":"%s","toAddress":"%s","opener":null,"openerEdited":false}]}"""
                        .formatted(candidateId, toAddress)))
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
