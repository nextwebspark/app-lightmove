package app.lightmove.api.outreach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingMailboxGateway;
import app.lightmove.api.RecordingMailboxGateway.SentRecord;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.outreach.service.ProviderAppUnavailable;
import app.lightmove.api.outreach.model.DeliveryFailure;
import app.lightmove.api.outreach.model.GrantedMailbox;
import app.lightmove.api.outreach.model.InboundMessage;
import app.lightmove.api.outreach.model.MailboxAccessWithdrawn;
import app.lightmove.api.outreach.model.MailboxGrants;
import app.lightmove.api.outreach.service.OutreachDispatcher;
import app.lightmove.api.outreach.service.OutreachInboxService;
import app.lightmove.api.outreach.service.OutreachSendService;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
 * The dispatcher, the reply webhook and the poll against a recording mailbox: the sending window, the
 * daily cap, follow-ups threaded under the first email, every way a run ends, two dispatchers racing,
 * and the Outreach page's reads. The clock is the test's: each pass is run at a moment it chose.
 */
@IntegrationTest
class OutreachDispatchIntegrationTest extends FlowTestSupport {

    private static final ZoneId DUBAI = ZoneId.of("Asia/Dubai");
    private static final String MAILBOX = "consultant@firm.example";
    private static final String STEP_ONE_BODY = "Hi {{firstName}},\n\n{{opener}}\n\nWe are hiring a {{positionTitle}}.";

    @Autowired private RecordingMailboxGateway gateway;
    @Autowired private OutreachDispatcher dispatcher;
    @Autowired private OutreachInboxService inbox;
    @Autowired private OutreachSendService sends;
    @Autowired private JdbcTemplate jdbc;

    private String consultant;
    private String projectId;
    private String clientId;
    private Instant monday;

    @BeforeEach
    void setUp() throws Exception {
        gateway.clear();
        // The suite shares one database: runs other classes left due would otherwise go out in these passes.
        jdbc.update("update app_lm_outreach_enrollment set status = 'STOPPED', stop_reason = 'MANUAL', "
                + "next_send_at = null, sending_since = null where status in ('SCHEDULED', 'ACTIVE')");
        String consultantEmail = "yara@" + domain;
        createWorkspace(verifiedUser("Yara Haddad", consultantEmail), "Meridian Search");
        consultant = login(consultantEmail);
        projectId = mandate();
        connectMailbox();
        monday = LocalDate.now(DUBAI).plusWeeks(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                .atTime(LocalTime.of(10, 0)).atZone(DUBAI).toInstant();
    }

    @Test
    @DisplayName("step 1 goes in the window, step 2 waits its working days and replies in the same thread")
    void followUpsThreadUnderTheFirstEmail() throws Exception {
        String sequenceId = createSequence("First approach");
        String priya = executive("Priya Raman", "priya@" + domain);
        start(sequenceId, priya, "priya@" + domain, "Your treasury work stood out.");

        dispatcher.dispatchAt(monday);

        List<SentRecord> first = sentTo("priya@" + domain);
        assertThat(first).hasSize(1);
        assertThat(first.getFirst().email().subject()).isEqualTo("Confidential: Group CFO");
        assertThat(first.getFirst().email().htmlBody())
                .isEqualTo("<p>Hi Priya,</p><p>Your treasury work stood out.</p><p>We are hiring a Group CFO.</p>");
        assertThat(first.getFirst().email().replyToMessageId()).isNull();
        Map<String, Object> afterFirst = enrollmentOf(priya);
        assertThat(afterFirst.get("status")).isEqualTo("ACTIVE");
        assertThat(((java.sql.Timestamp) afterFirst.get("next_send_at")).toInstant())
                .isEqualTo(monday.plus(Duration.ofDays(3)));
        assertThat(candidateStatus(priya)).isEqualTo("contacted");

        dispatcher.dispatchAt(monday.plus(Duration.ofDays(2)));
        assertThat(sentTo("priya@" + domain)).hasSize(1);

        dispatcher.dispatchAt(monday.plus(Duration.ofDays(3)));
        List<SentRecord> both = sentTo("priya@" + domain);
        assertThat(both).hasSize(2);
        SentRecord second = both.get(1);
        assertThat(second.email().subject()).isEqualTo("Re: Confidential: Group CFO");
        assertThat(second.email().htmlBody()).isEqualTo("<p>Following up, Priya.</p>");
        assertThat(second.email().replyToMessageId()).isEqualTo(afterFirst.get("last_message_id"));
        assertThat(enrollmentOf(priya).get("thread_id")).isEqualTo(afterFirst.get("thread_id"));

        assertThat(activityKinds(priya)).containsSubsequence("OUTREACH_ENROLLED", "EMAIL_SENT", "STATUS_CHANGED",
                "EMAIL_SENT");
        Integer stored = jdbc.queryForObject("select count(*) from app_lm_outreach_message where enrollment_id = ?::uuid",
                Integer.class, afterFirst.get("id").toString());
        assertThat(stored).isEqualTo(2);
    }

    @Test
    @DisplayName("a reconnect off Nylas stops a run at its next send, never replying into the old thread")
    void reconnectingOffNylasStopsTheRunAtItsNextSend() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        start(createSequence("First approach"), priya, "priya@" + domain, null);
        dispatcher.dispatchAt(monday);
        assertThat(enrollmentOf(priya)).containsEntry("thread_gateway", "NYLAS");

        gateway.grant(new GrantedMailbox(MailboxGrants.mintDirect("google"), MAILBOX, "google", "refresh-token-1"));
        connectMailbox();
        assertThat(enrollmentOf(priya).get("status")).isEqualTo("ACTIVE");
        dispatcher.dispatchAt(monday.plus(Duration.ofDays(3)));

        assertThat(sentTo("priya@" + domain)).hasSize(1);
        assertThat(enrollmentOf(priya)).containsEntry("status", "STOPPED").containsEntry("stop_reason", "MAILBOX_MOVED");
        assertThat(activityKinds(priya)).endsWith("OUTREACH_STOPPED");
        assertThat(jdbc.queryForObject("select count(*) from app_lm_audit_event where event_type = 'OUTREACH_STOPPED' "
                + "and metadata ->> 'reason' = 'MAILBOX_MOVED' and metadata ->> 'enrollmentId' = ?", Integer.class,
                enrollmentOf(priya).get("id").toString())).isEqualTo(1);
    }

    @Test
    @DisplayName("a disconnect and a fresh connect through another gateway stops the run just the same")
    void disconnectingThenConnectingElsewhereStopsTheRun() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        start(createSequence("First approach"), priya, "priya@" + domain, null);
        dispatcher.dispatchAt(monday);

        as(consultant, delete("/api/v1/outreach/mailbox")).andExpect(status().isNoContent());
        gateway.grant(new GrantedMailbox(MailboxGrants.mintDirect("google"), MAILBOX, "google", "refresh-token-1"));
        connectMailbox();
        dispatcher.dispatchAt(monday.plus(Duration.ofDays(3)));

        assertThat(sentTo("priya@" + domain)).hasSize(1);
        assertThat(enrollmentOf(priya)).containsEntry("stop_reason", "MAILBOX_MOVED");
    }

    @Test
    @DisplayName("nothing goes outside the sender's working day: a weekend send waits for Monday's window")
    void nothingGoesOutsideTheWindow() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        start(createSequence("First approach"), priya, "priya@" + domain, null);

        Instant saturday = monday.minus(Duration.ofDays(2));
        dispatcher.dispatchAt(saturday);
        dispatcher.dispatchAt(monday.minus(Duration.ofHours(3)));

        assertThat(sentTo("priya@" + domain)).isEmpty();
        Map<String, Object> waiting = enrollmentOf(priya);
        assertThat(waiting.get("status")).isEqualTo("SCHEDULED");
        assertThat(((java.sql.Timestamp) waiting.get("next_send_at")).toInstant())
                .isEqualTo(monday.minus(Duration.ofHours(2)));
        assertThat(waiting.get("sending_since")).isNull();
    }

    @Test
    @DisplayName("Start now sends the first email at once, even outside the window, the rest minutes apart")
    void startNowSendsAtOnce() throws Exception {
        String sequenceId = createSequence("First approach");
        String priya = executive("Priya Raman", "priya@" + domain);
        String rajesh = executive("Rajesh Menon", "rajesh@" + domain);
        startTimed(sequenceId, List.of(person(priya, "priya@" + domain, null), person(rajesh, "rajesh@" + domain, null)),
                ",\"startMode\":\"NOW\"")
                .andExpect(status().isCreated());

        Duration gap = Duration.between(nextSendAt(priya), nextSendAt(rajesh));
        assertThat(gap).isBetween(Duration.ofSeconds(60), Duration.ofSeconds(180));

        Instant saturday = monday.minus(Duration.ofDays(2));
        dispatcher.dispatchAt(saturday);

        assertThat(sentTo("priya@" + domain)).hasSize(1);
        assertThat(sentTo("rajesh@" + domain)).hasSize(1);
        assertThat(nextSendAt(priya)).isEqualTo(monday.plus(Duration.ofDays(2)));
    }

    @Test
    @DisplayName("a chosen start time holds the first email until then, and goes then even outside the window")
    void aChosenStartTimeHolds() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        Instant mondayEvening = monday.plus(Duration.ofHours(9));
        startTimed(createSequence("First approach"), List.of(person(priya, "priya@" + domain, null)),
                ",\"startMode\":\"AT\",\"startAt\":\"%s\"".formatted(mondayEvening))
                .andExpect(status().isCreated());

        dispatcher.dispatchAt(monday);
        assertThat(sentTo("priya@" + domain)).isEmpty();

        dispatcher.dispatchAt(mondayEvening);
        assertThat(sentTo("priya@" + domain)).hasSize(1);
    }

    @Test
    @DisplayName("a chosen start time must be ahead and within 60 days")
    void aChosenStartTimeIsChecked() throws Exception {
        String sequenceId = createSequence("First approach");
        String priya = executive("Priya Raman", "priya@" + domain);
        List<String> people = List.of(person(priya, "priya@" + domain, null));

        startTimed(sequenceId, people, ",\"startMode\":\"AT\",\"startAt\":\"%s\""
                .formatted(Instant.now().minus(Duration.ofHours(1))))
                .andExpect(status().isBadRequest());
        startTimed(sequenceId, people, ",\"startMode\":\"AT\",\"startAt\":\"%s\""
                .formatted(Instant.now().plus(Duration.ofDays(61))))
                .andExpect(status().isBadRequest());
        startTimed(sequenceId, people, ",\"startMode\":\"AT\"").andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("select count(*) from app_lm_outreach_enrollment where candidate_id = ?::uuid",
                Integer.class, priya)).isZero();
    }

    @Test
    @DisplayName("Start now still keeps the mailbox's daily cap")
    void startNowKeepsTheCap() throws Exception {
        jdbc.update("update app_lm_mailbox_connection set daily_cap = 1 where address = ? and workspace_id = "
                + "(select workspace_id from app_lm_project where id = ?::uuid)", MAILBOX, projectId);
        String priya = executive("Priya Raman", "priya@" + domain);
        String rajesh = executive("Rajesh Menon", "rajesh@" + domain);
        startTimed(createSequence("First approach"),
                List.of(person(priya, "priya@" + domain, null), person(rajesh, "rajesh@" + domain, null)),
                ",\"startMode\":\"NOW\"")
                .andExpect(status().isCreated());

        Instant saturday = monday.minus(Duration.ofDays(2));
        dispatcher.dispatchAt(saturday);
        dispatcher.dispatchAt(saturday.plus(Duration.ofHours(1)));

        assertThat(sentTo("priya@" + domain)).hasSize(1);
        assertThat(sentTo("rajesh@" + domain)).isEmpty();
        assertThat(nextSendAt(rajesh)).isEqualTo(monday.minus(Duration.ofHours(2)));
    }

    @Test
    @DisplayName("a sequence's own days and a follow-up's send time decide when it goes")
    void aSequencesOwnScheduleDecides() throws Exception {
        String steps = """
                [{"delayWorkingDays":0,"subject":"Confidential","body":"Hi {{firstName}}"},
                 {"delayWorkingDays":1,"body":"Following up.","sendTime":"09:30"}]""";
        String sequenceId = body(as(consultant, post(outreach("/sequences")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Gulf week","steps":%s,"schedule":{"days":["SUNDAY","MONDAY","TUESDAY","WEDNESDAY",
                         "THURSDAY"],"windowStart":"09:00","windowEnd":"17:00"}}""".formatted(steps)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        String priya = executive("Priya Raman", "priya@" + domain);
        String rajesh = executive("Rajesh Menon", "rajesh@" + domain);
        start(sequenceId, priya, "priya@" + domain, null);

        dispatcher.dispatchAt(monday);
        assertThat(sentTo("priya@" + domain)).hasSize(1);
        assertThat(nextSendAt(priya)).isEqualTo(monday.plus(Duration.ofDays(1)).minus(Duration.ofMinutes(30)));

        start(sequenceId, rajesh, "rajesh@" + domain, null);
        Instant friday = monday.plus(Duration.ofDays(4));
        dispatcher.dispatchAt(friday);
        assertThat(sentTo("rajesh@" + domain)).isEmpty();
        assertThat(nextSendAt(rajesh)).isEqualTo(monday.plus(Duration.ofDays(6)).minus(Duration.ofHours(1)));
    }

    @Test
    @DisplayName("the window is read in the consultant's own zone, which only a zone on the list can be")
    void theWindowIsReadInTheSendersZone() throws Exception {
        as(consultant, put("/api/v1/outreach/mailbox/time-zone").contentType(MediaType.APPLICATION_JSON)
                .content("{\"timeZone\":\"Mars/Olympus\"}"))
                .andExpect(status().isBadRequest());
        JsonNode mailbox = body(as(consultant, put("/api/v1/outreach/mailbox/time-zone")
                .contentType(MediaType.APPLICATION_JSON).content("{\"timeZone\":\"Europe/London\"}"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(mailbox.get("connection").get("timeZone").asText()).isEqualTo("Europe/London");

        String priya = executive("Priya Raman", "priya@" + domain);
        start(createSequence("First approach"), priya, "priya@" + domain, null);
        dispatcher.dispatchAt(monday);

        assertThat(sentTo("priya@" + domain)).isEmpty();
        assertThat(nextSendAt(priya)).isEqualTo(LocalDate.ofInstant(monday, DUBAI).atTime(LocalTime.of(8, 0))
                .atZone(ZoneId.of("Europe/London")).toInstant());
    }

    @Test
    @DisplayName("a mailbox's daily cap holds: the rest wait for the next working day's window")
    void theDailyCapHolds() throws Exception {
        jdbc.update("update app_lm_mailbox_connection set daily_cap = 1 where address = ? and workspace_id = "
                + "(select workspace_id from app_lm_project where id = ?::uuid)", MAILBOX, projectId);
        String sequenceId = createSequence("First approach");
        String priya = executive("Priya Raman", "priya@" + domain);
        String rajesh = executive("Rajesh Menon", "rajesh@" + domain);
        start(sequenceId, List.of(person(priya, "priya@" + domain, null), person(rajesh, "rajesh@" + domain, null)));

        dispatcher.dispatchAt(monday);
        dispatcher.dispatchAt(monday.plus(Duration.ofHours(1)));

        assertThat(sentTo("priya@" + domain).size() + sentTo("rajesh@" + domain).size()).isEqualTo(1);
        String waiting = sentTo("priya@" + domain).isEmpty() ? priya : rajesh;
        assertThat(((java.sql.Timestamp) enrollmentOf(waiting).get("next_send_at")).toInstant())
                .isEqualTo(monday.plus(Duration.ofDays(1)).minus(Duration.ofHours(2)));
    }

    @Test
    @DisplayName("do not contact set mid-sequence: nothing more goes, and the run stops with that reason")
    void doNotContactStopsTheRun() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        start(createSequence("First approach"), priya, "priya@" + domain, null);
        dispatcher.dispatchAt(monday);
        markDoNotContact(priya);

        dispatcher.dispatchAt(monday.plus(Duration.ofDays(3)));

        assertThat(sentTo("priya@" + domain)).hasSize(1);
        assertThat(enrollmentOf(priya)).containsEntry("status", "STOPPED").containsEntry("stop_reason", "DO_NOT_CONTACT");
        assertThat(activityKinds(priya)).contains("OUTREACH_STOPPED");
    }

    @Test
    @DisplayName("out of the running or the address removed: the send-time re-check stops the run")
    void theReCheckStopsWhatStartWouldRefuse() throws Exception {
        String sequenceId = createSequence("First approach");
        String priya = executive("Priya Raman", "priya@" + domain);
        String rajesh = executive("Rajesh Menon", "rajesh@" + domain);
        start(sequenceId, List.of(person(priya, "priya@" + domain, null), person(rajesh, "rajesh@" + domain, null)));
        setStatus(priya, "notInterested");
        jdbc.update("delete from app_lm_person_contact where lower(value) = ?", "rajesh@" + domain);

        dispatcher.dispatchAt(monday);

        assertThat(gateway.sent()).isEmpty();
        assertThat(enrollmentOf(priya)).containsEntry("stop_reason", "LEFT_THE_RUNNING");
        assertThat(enrollmentOf(rajesh)).containsEntry("stop_reason", "ADDRESS_REMOVED");
    }

    @Test
    @DisplayName("the first send moves Identified to Contacted, and leaves someone already Engaged where they are")
    void theFirstSendOnlyMovesStatusForward() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        start(createSequence("First approach"), priya, "priya@" + domain, null);
        setStatus(priya, "engaged");

        dispatcher.dispatchAt(monday);

        assertThat(sentTo("priya@" + domain)).hasSize(1);
        assertThat(candidateStatus(priya)).isEqualTo("engaged");
    }

    @Test
    @DisplayName("a signed reply stops the sequence and nothing after it goes; a forged one is refused")
    void aReplyStopsTheSequence() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        start(createSequence("First approach"), priya, "priya@" + domain, null);
        dispatcher.dispatchAt(monday);
        String thread = (String) enrollmentOf(priya).get("thread_id");

        gateway.deliverNext(List.of(new InboundMessage("grant-1", thread, "priya@" + domain)));
        mvc.perform(post("/api/v1/outreach/webhooks/mailbox").header("X-Nylas-Signature", "forged")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        assertThat(enrollmentOf(priya).get("status")).isEqualTo("ACTIVE");

        // The mailbox's own sent copy lands in the thread too, and is not an answer.
        gateway.deliverNext(List.of(new InboundMessage("grant-1", thread, MAILBOX)));
        webhook();
        assertThat(enrollmentOf(priya).get("status")).isEqualTo("ACTIVE");

        gateway.deliverNext(List.of(new InboundMessage("grant-1", thread, "priya@" + domain)));
        webhook();

        Map<String, Object> answered = enrollmentOf(priya);
        assertThat(answered.get("status")).isEqualTo("REPLIED");
        assertThat(answered.get("replied_at")).isNotNull();
        dispatcher.dispatchAt(monday.plus(Duration.ofDays(3)));
        assertThat(sentTo("priya@" + domain)).hasSize(1);
        assertThat(activityKinds(priya)).contains("EMAIL_REPLIED");
    }

    @Test
    @DisplayName("the challenge is echoed, and only when it is a plain token")
    void theWebhookChallengeIsEchoed() throws Exception {
        mvc.perform(get("/api/v1/outreach/webhooks/mailbox").param("challenge", "abc-123_XYZ"))
                .andExpect(status().isOk())
                .andExpect(content().string("abc-123_XYZ"));
        mvc.perform(get("/api/v1/outreach/webhooks/mailbox").param("challenge", "<script>"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a bounce ends the run, and a mail daemon writing into the thread is a bounce, not a reply")
    void bouncesEndTheRun() throws Exception {
        String sequenceId = createSequence("First approach");
        String priya = executive("Priya Raman", "priya@" + domain);
        String rajesh = executive("Rajesh Menon", "rajesh@" + domain);
        start(sequenceId, List.of(person(priya, "priya@" + domain, null), person(rajesh, "rajesh@" + domain, null)));
        dispatcher.dispatchAt(monday);

        gateway.deliverNext(List.of(new DeliveryFailure("grant-1", (String) enrollmentOf(priya).get("thread_id"), null)));
        webhook();
        gateway.deliverNext(List.of(new InboundMessage("grant-1", (String) enrollmentOf(rajesh).get("thread_id"),
                "MAILER-DAEMON@googlemail.com")));
        webhook();

        assertThat(enrollmentOf(priya).get("status")).isEqualTo("BOUNCED");
        assertThat(enrollmentOf(rajesh).get("status")).isEqualTo("BOUNCED");
    }

    @Test
    @DisplayName("Exchange Online's non-delivery report, found by the poll, is a bounce, not a reply")
    void anExchangeNonDeliveryReportIsABounce() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        start(createSequence("First approach"), priya, "priya@" + domain, null);
        dispatcher.dispatchAt(monday);

        gateway.writeInto((String) enrollmentOf(priya).get("thread_id"),
                "MicrosoftExchange329e71ec88ae4615bbc36ab6ce41109e@meridian.example");
        inbox.pollListeningThreads(monday.plus(Duration.ofHours(1)));

        assertThat(enrollmentOf(priya).get("status")).isEqualTo("BOUNCED");
        assertThat(activityKinds(priya)).doesNotContain("EMAIL_REPLIED");
    }

    @Test
    @DisplayName("the poll finds a reply no webhook delivered")
    void thePollFindsAMissedReply() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        start(createSequence("First approach"), priya, "priya@" + domain, null);
        dispatcher.dispatchAt(monday);
        String thread = (String) enrollmentOf(priya).get("thread_id");
        gateway.writeInto(thread, MAILBOX);

        inbox.pollListeningThreads(monday.plus(Duration.ofHours(1)));
        assertThat(enrollmentOf(priya).get("status")).isEqualTo("ACTIVE");

        gateway.writeInto(thread, "Priya@" + domain);
        inbox.pollListeningThreads(monday.plus(Duration.ofHours(2)));
        assertThat(enrollmentOf(priya).get("status")).isEqualTo("REPLIED");
    }

    @Test
    @DisplayName("a mailbox whose access was withdrawn sends nothing more; its runs stop")
    void aWithdrawnMailboxStops() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        start(createSequence("First approach"), priya, "priya@" + domain, null);

        gateway.deliverNext(List.of(new MailboxAccessWithdrawn("grant-1")));
        webhook();
        dispatcher.dispatchAt(monday);

        assertThat(sentTo("priya@" + domain)).isEmpty();
        assertThat(enrollmentOf(priya)).containsEntry("stop_reason", "MAILBOX_INACTIVE");
    }

    @Test
    @DisplayName("two dispatchers at once never send one email twice")
    void twoDispatchersNeverDoubleSend() throws Exception {
        String sequenceId = createSequence("First approach");
        List<String> people = new java.util.ArrayList<>();
        for (int index = 0; index < 8; index++) {
            String address = "exec" + index + "@" + domain;
            people.add(person(executive("Executive " + index, address), address, null));
        }
        start(sequenceId, people);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<?>> passes = List.of(
                    pool.submit(() -> { await(go); dispatcher.dispatchAt(monday); }),
                    pool.submit(() -> { await(go); dispatcher.dispatchAt(monday); }));
            go.countDown();
            for (Future<?> pass : passes) {
                pass.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        for (int index = 0; index < 8; index++) {
            assertThat(sentTo("exec" + index + "@" + domain)).hasSize(1);
        }
    }

    @Test
    @DisplayName("a claim its dispatcher never released is stopped as uncertain, never sent again")
    void anAbandonedClaimIsNeverResent() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        start(createSequence("First approach"), priya, "priya@" + domain, null);
        jdbc.update("update app_lm_outreach_enrollment set sending_since = ? where candidate_id = ?::uuid",
                java.sql.Timestamp.from(monday.minus(Duration.ofHours(1))), priya);

        dispatcher.dispatchAt(monday);

        assertThat(sentTo("priya@" + domain)).isEmpty();
        assertThat(enrollmentOf(priya)).containsEntry("stop_reason", "SEND_UNCERTAIN");
    }

    @Test
    @DisplayName("a reply that lands while the row waits in a claimed batch keeps it Replied, never stopped later")
    void aReplyBetweenClaimAndSendStands() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        start(createSequence("First approach"), priya, "priya@" + domain, null);
        dispatcher.dispatchAt(monday);
        Instant thursday = monday.plus(Duration.ofDays(3));
        List<java.util.UUID> claimed = sends.claimDue(thursday);
        java.util.UUID run = java.util.UUID.fromString(enrollmentOf(priya).get("id").toString());
        assertThat(claimed).contains(run);

        gateway.deliverNext(List.of(new InboundMessage("grant-1", (String) enrollmentOf(priya).get("thread_id"),
                "priya@" + domain)));
        webhook();
        sends.sendClaimed(run, thursday);
        dispatcher.dispatchAt(thursday.plus(Duration.ofMinutes(11)));

        assertThat(sentTo("priya@" + domain)).hasSize(1);
        assertThat(enrollmentOf(priya)).containsEntry("status", "REPLIED").containsEntry("sending_since", null);
        assertThat(activityKinds(priya)).doesNotContain("OUTREACH_STOPPED");
    }

    @Test
    @DisplayName("a rate-limited send went nowhere, so it waits a minute and goes; a 5xx may have gone, so it stops")
    void knownUnsentFailuresWait() throws Exception {
        String sequenceId = createSequence("First approach");
        String priya = executive("Priya Raman", "priya@" + domain);
        start(sequenceId, priya, "priya@" + domain, null);
        gateway.failSendsWith(new VendorException(VendorCall.of("nylas", "send"), VendorFailureKind.RATE_LIMITED, null));

        dispatcher.dispatchAt(monday);

        assertThat(enrollmentOf(priya)).containsEntry("status", "SCHEDULED").containsEntry("sending_since", null);
        assertThat(activityKinds(priya)).doesNotContain("OUTREACH_STOPPED");
        gateway.failSendsWith(null);
        dispatcher.dispatchAt(monday.plus(Duration.ofMinutes(1)));
        assertThat(sentTo("priya@" + domain)).hasSize(1);

        String rajesh = executive("Rajesh Menon", "rajesh@" + domain);
        start(sequenceId, rajesh, "rajesh@" + domain, null);
        gateway.failSendsWith(new VendorException(VendorCall.of("nylas", "send"), VendorFailureKind.UNAVAILABLE, null));
        dispatcher.dispatchAt(monday.plus(Duration.ofMinutes(2)));
        assertThat(enrollmentOf(rajesh)).containsEntry("stop_reason", "SEND_UNCERTAIN");
    }

    @Test
    @DisplayName("a workspace mail app the provider refuses holds the run without stopping it or the mailbox")
    void aRefusedWorkspaceAppHoldsTheRun() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        start(createSequence("First approach"), priya, "priya@" + domain, null);
        gateway.failSendsWith(new ProviderAppUnavailable("invalid_client"));

        dispatcher.dispatchAt(monday);

        assertThat(enrollmentOf(priya)).containsEntry("status", "SCHEDULED").containsEntry("stop_reason", null);
        assertThat(activityKinds(priya)).doesNotContain("OUTREACH_STOPPED");
        assertThat(jdbc.queryForObject("select status from app_lm_mailbox_connection where workspace_id = "
                + "(select workspace_id from app_lm_project where id = ?::uuid)", String.class, projectId))
                .isEqualTo("ACTIVE");
        gateway.failSendsWith(null);
        dispatcher.dispatchAt(monday.plus(Duration.ofMinutes(1)));
        assertThat(sentTo("priya@" + domain)).isEmpty();
        dispatcher.dispatchAt(monday.plus(Duration.ofHours(1)));
        assertThat(sentTo("priya@" + domain)).hasSize(1);
    }

    @Test
    @DisplayName("a send the mail service refuses is not retried; the run stops")
    void aRefusedSendIsNotRetried() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        start(createSequence("First approach"), priya, "priya@" + domain, null);
        gateway.failSendsWith(new IllegalStateException("refused"));

        dispatcher.dispatchAt(monday);
        gateway.failSendsWith(null);
        dispatcher.dispatchAt(monday.plus(Duration.ofHours(1)));

        assertThat(sentTo("priya@" + domain)).isEmpty();
        assertThat(enrollmentOf(priya)).containsEntry("stop_reason", "SEND_FAILED");
    }

    @Test
    @DisplayName("the Outreach page counts and lists runs, the drawer lists steps, and Stop ends a run once")
    void thePageTheDrawerAndStop() throws Exception {
        String sequenceId = createSequence("First approach");
        String priya = executive("Priya Raman", "priya@" + domain);
        String rajesh = executive("Rajesh Menon", "rajesh@" + domain);
        start(sequenceId, List.of(person(priya, "priya@" + domain, null), person(rajesh, "rajesh@" + domain, null)));
        dispatcher.dispatchAt(monday);
        gateway.deliverNext(List.of(new InboundMessage("grant-1", (String) enrollmentOf(priya).get("thread_id"),
                "priya@" + domain)));
        webhook();

        JsonNode page = body(as(consultant, get(outreach("/people"))).andExpect(status().isOk()).andReturn());
        assertThat(page.get("counts").get("enrolled").asInt()).isEqualTo(2);
        assertThat(page.get("counts").get("emailsSent").asInt()).isEqualTo(2);
        assertThat(page.get("counts").get("replied").asInt()).isEqualTo(1);
        assertThat(page.get("counts").get("inFlight").asInt()).isEqualTo(1);
        assertThat(page.get("people")).hasSize(2);
        JsonNode priyaRow = rowOf(page, priya);
        assertThat(priyaRow.get("status").asText()).isEqualTo("REPLIED");
        assertThat(priyaRow.get("sentCount").asInt()).isEqualTo(1);
        assertThat(priyaRow.get("stepCount").asInt()).isEqualTo(3);
        assertThat(priyaRow.get("senderName").asText()).isEqualTo("Yara Haddad");
        assertThat(priyaRow.get("candidateStatus").asText()).isEqualTo("contacted");

        JsonNode drawer = body(as(consultant, get(outreach("/candidates/" + priya))).andExpect(status().isOk()).andReturn());
        assertThat(drawer.get("steps")).hasSize(3);
        assertThat(drawer.get("steps").get(0).get("state").asText()).isEqualTo("SENT");
        assertThat(drawer.get("steps").get(1).get("state").asText()).isEqualTo("NOT_SENT");
        assertThat(drawer.get("steps").get(1).get("notSentBecause").asText()).isEqualTo("REPLIED");

        JsonNode rajeshDrawer = body(as(consultant, get(outreach("/candidates/" + rajesh))).andReturn());
        assertThat(rajeshDrawer.get("steps").get(1).get("state").asText()).isEqualTo("SCHEDULED");
        assertThat(rajeshDrawer.get("steps").get(2).get("state").asText()).isEqualTo("WAITING");

        String rajeshRun = rowOf(page, rajesh).get("id").asText();
        as(consultant, post(outreach("/enrollments/" + rajeshRun + "/stop"))).andExpect(status().isNoContent());
        as(consultant, post(outreach("/enrollments/" + rajeshRun + "/stop"))).andExpect(status().isConflict());
        assertThat(enrollmentOf(rajesh)).containsEntry("stop_reason", "MANUAL");
        dispatcher.dispatchAt(monday.plus(Duration.ofDays(3)));
        assertThat(sentTo("rajesh@" + domain)).hasSize(1);

        JsonNode sequences = body(as(consultant, get(outreach("/sequences"))).andReturn()).get("sequences");
        assertThat(sequences.get(0).get("sentCount").asInt()).isEqualTo(2);
        assertThat(sequences.get(0).get("repliedCount").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("another workspace cannot read or stop these runs, and a client seat is refused")
    void tenantIsolationAndClientSeats() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        start(createSequence("First approach"), priya, "priya@" + domain, null);
        String run = rowOf(body(as(consultant, get(outreach("/people"))).andReturn()), priya).get("id").asText();

        String rep = clientSeat();
        for (MockHttpServletRequestBuilder route : List.of(get(outreach("/people")),
                get(outreach("/candidates/" + priya)), post(outreach("/enrollments/" + run + "/stop")))) {
            as(rep, route).andExpect(status().isForbidden());
        }

        String outsiderEmail = "omar@other-" + domain;
        createWorkspace(verifiedUser("Omar Farouk", outsiderEmail), "Other Search");
        String outsider = login(outsiderEmail);
        String theirProject = body(as(outsider, post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"%s\",\"positionTitle\":\"CTO\"}".formatted(ownClient(outsider))))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        as(outsider, get(outreach("/people"))).andExpect(status().isNotFound());
        as(outsider, post("/api/v1/projects/" + theirProject + "/outreach/enrollments/" + run + "/stop"))
                .andExpect(status().isNotFound());
        JsonNode theirs = body(as(outsider, get("/api/v1/projects/" + theirProject + "/outreach/people")).andReturn());
        assertThat(theirs.get("people")).isEmpty();
        assertThat(enrollmentOf(priya).get("status")).isEqualTo("SCHEDULED");
    }

    private static void await(CountDownLatch go) {
        try {
            go.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void webhook() throws Exception {
        mvc.perform(post("/api/v1/outreach/webhooks/mailbox")
                        .header("X-Nylas-Signature", RecordingMailboxGateway.VALID_SIGNATURE)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    private List<SentRecord> sentTo(String address) {
        return gateway.sent().stream().filter(record -> record.email().to().equalsIgnoreCase(address)).toList();
    }

    private Map<String, Object> enrollmentOf(String candidateId) {
        return jdbc.queryForMap("select * from app_lm_outreach_enrollment where candidate_id = ?::uuid", candidateId);
    }

    private List<String> activityKinds(String candidateId) {
        return jdbc.queryForList("select a.kind from app_lm_person_activity a join app_lm_project_candidate c "
                + "on c.person_id = a.person_id where c.id = ?::uuid order by a.occurred_at, a.id", String.class, candidateId);
    }

    private String candidateStatus(String candidateId) throws Exception {
        return body(as(consultant, get("/api/v1/projects/" + projectId + "/candidates/" + candidateId)).andReturn())
                .get("status").asText();
    }

    private void setStatus(String candidateId, String status) throws Exception {
        as(consultant, patch("/api/v1/projects/" + projectId + "/candidates/" + candidateId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"%s\"}".formatted(status)))
                .andExpect(status().isOk());
    }

    private static JsonNode rowOf(JsonNode page, String candidateId) {
        for (JsonNode row : page.get("people")) {
            if (candidateId.equals(row.get("candidateId").asText())) {
                return row;
            }
        }
        throw new AssertionError("No outreach row for " + candidateId);
    }

    private void start(String sequenceId, String candidateId, String address, String opener) throws Exception {
        start(sequenceId, List.of(person(candidateId, address, opener)));
    }

    private void start(String sequenceId, List<String> people) throws Exception {
        startTimed(sequenceId, people, "").andExpect(status().isCreated());
    }

    private ResultActions startTimed(String sequenceId, List<String> people, String timingJson) throws Exception {
        return as(consultant, post(outreach("/sequences/" + sequenceId + "/enrollments"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"people\":[" + String.join(",", people) + "]" + timingJson + "}"));
    }

    private Instant nextSendAt(String candidateId) {
        return ((java.sql.Timestamp) enrollmentOf(candidateId).get("next_send_at")).toInstant();
    }

    private static String person(String candidateId, String toAddress, String opener) {
        return """
                {"candidateId":"%s","toAddress":"%s","opener":%s,"openerEdited":false}"""
                .formatted(candidateId, toAddress, opener == null ? "null" : "\"" + opener + "\"");
    }

    private String createSequence(String name) throws Exception {
        String steps = String.join(",", List.of(
                """
                {"delayWorkingDays":0,"subject":"Confidential: {{positionTitle}}","body":"%s"}"""
                        .formatted(STEP_ONE_BODY.replace("\n", "\\n")),
                """
                {"delayWorkingDays":3,"body":"Following up, {{firstName}}."}""",
                """
                {"delayWorkingDays":2,"body":"One last note, {{firstName}}."}"""));
        return body(as(consultant, post(outreach("/sequences")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\",\"steps\":[%s]}".formatted(name, steps)))
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

    private void markDoNotContact(String candidateId) throws Exception {
        String personId = body(as(consultant, get("/api/v1/projects/" + projectId + "/candidates/" + candidateId))
                .andReturn()).get("personId").asText();
        as(consultant, put("/api/v1/candidates/" + personId + "/do-not-contact")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"doNotContact\":true,\"reason\":\"Asked not to be approached.\"}"))
                .andExpect(status().isOk());
    }

    private String mandate() throws Exception {
        clientId = ownClient(consultant);
        return body(as(consultant, post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"%s\",\"positionTitle\":\"Group CFO\"}".formatted(clientId)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private String ownClient(String token) throws Exception {
        return body(as(token, post("/api/v1/clients").contentType(MediaType.APPLICATION_JSON)
                .content("{\"customName\":\"Acme Holdings\"}"))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private String clientSeat() throws Exception {
        String repEmail = "rep@client-" + domain;
        String representativeId = body(as(consultant, post("/api/v1/clients/" + clientId + "/representatives")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"Rep Person","position":"Advisor","email":"%s"}""".formatted(repEmail)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        String rep = body(mvc.perform(post("/api/v1/onboarding/accept-invitation-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","fullName":"Rep Person","password":"%s"}
                                """.formatted(email.latestTokenFor(repEmail), PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn()).get("accessToken").asText();
        as(consultant, post("/api/v1/projects/" + projectId + "/representatives")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"representativeId\":\"%s\"}".formatted(representativeId)))
                .andExpect(status().is2xxSuccessful());
        return rep;
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

    private String outreach(String path) {
        return "/api/v1/projects/" + projectId + "/outreach" + path;
    }
}
