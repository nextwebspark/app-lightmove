package app.lightmove.api.notetaker;

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
import app.lightmove.api.RecordingRecallBotApi;
import app.lightmove.api.StubChatModel;
import app.lightmove.api.notetaker.model.RecallBotRequest;
import app.lightmove.api.notetaker.model.RecallBotState;
import app.lightmove.api.notetaker.model.TranscriptLine;
import app.lightmove.api.notetaker.service.NotetakerReconcile;
import app.lightmove.api.notetaker.service.NotetakerTranscriptPurge;
import app.lightmove.api.outreach.model.CalendarEvent;
import app.lightmove.api.outreach.model.CalendarEventChanged;
import app.lightmove.api.outreach.model.GrantedMailbox;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
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
 * The Notetaker against a recording Recall: a bot sent to a kept call, claimed once, followed through Recall's own
 * webhook bodies, and its transcript filed as one Meeting note with Recall's copy deleted after.
 */
@IntegrationTest
class NotetakerIntegrationTest extends FlowTestSupport {

    private static final String MAILBOX = "consultant@firm.example";
    private static final String SUMMARY_MARKER = "writing the note of a call";
    private static final String SUMMARY = """
            {"overview":"Spoke with Priya about the Group CFO role; she is open to a move.",
             "keyPoints":["Leads a finance team of forty"],"motivation":"Wants a listed company","package":null,
             "notice":"Three months","mobility":null,"concerns":[],"nextSteps":["Send her the brief"],
             "competencyEvidence":[],"competenciesNotCovered":[],"briefConcerns":[]}""";

    @Autowired private RecordingMailboxGateway gateway;
    @Autowired private RecordingRecallBotApi recall;
    @Autowired private StubChatModel chat;
    @Autowired private NotetakerReconcile reconcile;
    @Autowired private NotetakerTranscriptPurge transcriptPurge;
    @Autowired private JdbcTemplate jdbc;

    private String grantId;
    private String consultant;
    private String projectId;
    private String clientId;
    private String priya;
    private Instant start;

    @BeforeEach
    void setUp() throws Exception {
        gateway.clear();
        recall.clear();
        chat.reset();
        grantId = "grant-" + domain;
        gateway.grant(new GrantedMailbox(grantId, MAILBOX, "google"));
        String consultantEmail = "yara@" + domain;
        createWorkspace(verifiedUser("Yara Haddad", consultantEmail), "Meridian Notes");
        consultant = login(consultantEmail);
        projectId = mandate();
        priya = executive("Priya Raman", "priya@" + domain);
        connectMailbox();
        start = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(Duration.ofDays(2));
        gateway.deliverNext(List.of(new CalendarEventChanged(grantId, new CalendarEvent("call-1",
                "First conversation", start, start.plus(Duration.ofMinutes(30)), List.of(MAILBOX, "priya@" + domain),
                "https://meet.google.com/abc-defg-hij", "Google Meet"))));
        mvc.perform(post("/api/v1/outreach/webhooks/mailbox")
                        .header("X-Nylas-Signature", RecordingMailboxGateway.VALID_SIGNATURE)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    @AfterEach
    void tearDown() {
        chat.reset();
        recall.clear();
    }

    @Test
    @DisplayName("a bot is scheduled for the call's start, and a call is recorded by one bot whoever presses again")
    void schedulesOneBotPerCall() throws Exception {
        JsonNode scheduled = body(schedule().andExpect(status().isCreated()).andReturn());

        assertThat(scheduled.get("status").asText()).isEqualTo("SCHEDULED");
        assertThat(recall.sent()).hasSize(1);
        RecallBotRequest request = recall.sent().getFirst();
        assertThat(request.meetingUrl()).isEqualTo("https://meet.google.com/abc-defg-hij");
        assertThat(request.joinAt()).isEqualTo(start.minus(Duration.ofMinutes(1)));
        assertThat(request.botName()).isEqualTo("Uncava Notetaker");
        assertThat(request.consentLine()).contains("Yara Haddad");
        assertThat(request.metadata()).containsEntry("recordingId", scheduled.get("id").asText());

        schedule().andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOTETAKER_ALREADY_SCHEDULED"));
        assertThat(recall.sent()).hasSize(1);

        JsonNode listed = notetaker();
        assertThat(listed.get("offered").asBoolean()).isTrue();
        assertThat(listed.get("recordings")).hasSize(1);
        assertThat(listed.get("recordings").get(0).get("meetingId").asText()).isEqualTo(meetingId());
        assertThat(jdbc.queryForObject("select count(*) from app_lm_audit_event where event_type = "
                + "'NOTETAKER_SCHEDULED'", Integer.class)).isPositive();
    }

    @Test
    @DisplayName("Recall's webhook walks the bot through the call, and its transcript becomes one Meeting note")
    void transcriptBecomesAMeetingNote() throws Exception {
        chat.answerWhenSystemContains(SUMMARY_MARKER, SUMMARY);
        String recordingId = scheduledRecordingId();
        String bot = recall.lastBotId();
        recall.holdsTranscript("t-1", List.of(
                new TranscriptLine(start.plusSeconds(5), "Yara Haddad", "Thanks for making the time.", true),
                new TranscriptLine(start.plusSeconds(20), "Priya Raman", "Happy to talk. My notice is three months.",
                        true)));

        delivered(bot, """
                {"event":"bot.joining_call","data":{"bot":{"id":"%s"},"data":{"code":"joining_call"}}}""", "nope")
                .andExpect(status().isUnauthorized());
        assertThat(statusOf(recordingId)).isEqualTo("SCHEDULED");

        deliver(bot, """
                {"event":"bot.joining_call","data":{"bot":{"id":"%s"},"data":{"code":"joining_call"}}}""");
        assertThat(statusOf(recordingId)).isEqualTo("JOINING");
        deliver(bot, """
                {"event":"bot.in_call_recording","data":{"bot":{"id":"%s"},"data":{"code":"in_call_recording"}}}""");
        assertThat(statusOf(recordingId)).isEqualTo("RECORDING");
        deliver(bot, """
                {"event":"bot.done","data":{"bot":{"id":"%s"},"data":{"code":"done","sub_code":null}}}""");
        assertThat(statusOf(recordingId)).isEqualTo("PROCESSING");
        deliver(bot, """
                {"event":"transcript.done","data":{"bot":{"id":"%s"},"transcript":{"id":"t-1"}}}""");

        assertThat(statusOf(recordingId)).isEqualTo("DONE");
        String body = jdbc.queryForObject("select n.body from app_lm_person_note n join app_lm_meeting_recording r "
                + "on r.note_id = n.id where r.id = ?::uuid and n.kind = 'MEETING' and n.body_format = 'MARKDOWN' "
                + "and n.project_id = r.project_id", String.class, recordingId);
        assertThat(body).startsWith("Spoke with Priya about the Group CFO role")
                .contains("- **Notice:** Three months", "## Next steps");
        assertThat(chat.lastPrompt().getUserMessage().getText()).contains("My notice is three months.")
                .contains("Group CFO");
        assertThat(recall.mediaDeleted()).containsExactly(bot);

        JsonNode transcript = body(as(consultant, get(notetakerPath() + "/" + recordingId + "/transcript"))
                .andExpect(status().isOk()).andReturn());
        assertThat(transcript.get("lines")).hasSize(2);
        assertThat(transcript.get("lines").get(1).get("speaker").asText()).isEqualTo("Priya Raman");

        // A late delivery about a finished recording changes nothing.
        deliver(bot, """
                {"event":"bot.fatal","data":{"bot":{"id":"%s"},"data":{"code":"fatal","sub_code":"x"}}}""");
        assertThat(statusOf(recordingId)).isEqualTo("DONE");
    }

    @Test
    @DisplayName("a summary that fails leaves the recording to try again, and Recall's copy is kept until it works")
    void failedSummaryIsTriedAgain() throws Exception {
        chat.answerWhenSystemContains(SUMMARY_MARKER, "{}");
        String recordingId = scheduledRecordingId();
        String bot = recall.lastBotId();
        recall.holdsTranscript("t-1", List.of(new TranscriptLine(start, "Priya Raman", "Hello.", true)));
        deliver(bot, """
                {"event":"bot.in_call_recording","data":{"bot":{"id":"%s"},"data":{"code":"in_call_recording"}}}""");
        deliver(bot, """
                {"event":"transcript.done","data":{"bot":{"id":"%s"},"transcript":{"id":"t-1"}}}""");

        JsonNode failed = notetaker().get("recordings").get(0);
        assertThat(failed.get("status").asText()).isEqualTo("FAILED");
        assertThat(failed.get("failureReason").asText()).isEqualTo("SUMMARY_FAILED");
        assertThat(failed.get("retryable").asBoolean()).isTrue();
        assertThat(recall.mediaDeleted()).isEmpty();

        chat.answerWhenSystemContains(SUMMARY_MARKER, SUMMARY);
        as(consultant, post(notetakerPath() + "/" + recordingId + "/retry")).andExpect(status().isOk());

        assertThat(statusOf(recordingId)).isEqualTo("DONE");
        assertThat(recall.mediaDeleted()).containsExactly(bot);
        as(consultant, post(notetakerPath() + "/" + recordingId + "/retry")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOTETAKER_NOT_CHANGEABLE"));
    }

    @Test
    @DisplayName("a bot nobody let in fails with Recall's reason; a called-back one frees the call for another")
    void botsThatNeverRecordedAndCancellations() throws Exception {
        String first = scheduledRecordingId();
        String bot = recall.lastBotId();
        deliver(bot, """
                {"event":"bot.in_waiting_room","data":{"bot":{"id":"%s"},"data":{"code":"in_waiting_room"}}}""");
        deliver(bot, """
                {"event":"bot.call_ended","data":{"bot":{"id":"%s"},
                 "data":{"code":"call_ended","sub_code":"timeout_exceeded_waiting_room"}}}""");
        JsonNode failed = notetaker().get("recordings").get(0);
        assertThat(failed.get("status").asText()).isEqualTo("FAILED");
        assertThat(failed.get("failureReason").asText()).isEqualTo("timeout_exceeded_waiting_room");
        assertThat(failed.get("retryable").asBoolean()).isFalse();

        String second = scheduledRecordingId();
        assertThat(second).isNotEqualTo(first);
        as(consultant, delete(notetakerPath() + "/" + second)).andExpect(status().isNoContent());
        assertThat(statusOf(second)).isEqualTo("CANCELLED");
        assertThat(recall.removed()).containsExactly(recall.lastBotId());
        as(consultant, delete(notetakerPath() + "/" + second)).andExpect(status().isConflict());

        schedule().andExpect(status().isCreated());
        assertThat(recall.sent()).hasSize(3);
    }

    @Test
    @DisplayName("a recording whose webhooks never came is read back from Recall once its call is long over")
    void reconcileReadsBackLostDeliveries() throws Exception {
        chat.answerWhenSystemContains(SUMMARY_MARKER, SUMMARY);
        String recordingId = scheduledRecordingId();
        String bot = recall.lastBotId();
        recall.holdsTranscript("t-9", List.of(new TranscriptLine(start, "Priya Raman", "Hello.", true)));
        recall.botIs(bot, new RecallBotState("done", null, start.plus(Duration.ofMinutes(30)), "t-9", "done"));

        assertThat(reconcile.reconcileAt(start)).isZero();
        assertThat(statusOf(recordingId)).isEqualTo("SCHEDULED");

        reconcile.reconcileAt(start.plus(Duration.ofHours(2)));

        assertThat(statusOf(recordingId)).isEqualTo("DONE");
        assertThat(recall.mediaDeleted()).containsExactly(bot);
    }

    @Test
    @DisplayName("a transcript delivered twice is summarised and filed once")
    void aRedeliveredTranscriptFilesOneNote() throws Exception {
        chat.answerWhenSystemContains(SUMMARY_MARKER, SUMMARY);
        String recordingId = scheduledRecordingId();
        String bot = recall.lastBotId();
        recall.holdsTranscript("t-1", List.of(new TranscriptLine(start, "Priya Raman", "Hello.", true)));
        deliver(bot, """
                {"event":"bot.in_call_recording","data":{"bot":{"id":"%s"},"data":{"code":"in_call_recording"}}}""");
        deliver(bot, """
                {"event":"transcript.done","data":{"bot":{"id":"%s"},"transcript":{"id":"t-1"}}}""");
        deliver(bot, """
                {"event":"transcript.done","data":{"bot":{"id":"%s"},"transcript":{"id":"t-1"}}}""");
        // The reconcile handing the same transcript over again finds nothing left to do either.
        recall.botIs(bot, new RecallBotState("done", null, start, "t-1", "done"));
        reconcile.reconcileAt(start.plus(Duration.ofHours(2)));

        assertThat(statusOf(recordingId)).isEqualTo("DONE");
        assertThat(jdbc.queryForObject("select count(*) from app_lm_person_note n join app_lm_meeting_recording r "
                + "on r.person_id = n.person_id where r.id = ?::uuid and n.kind = 'MEETING'", Integer.class,
                recordingId)).isEqualTo(1);
        assertThat(chat.prompts().stream().filter(prompt -> prompt.getSystemMessage().getText()
                .contains(SUMMARY_MARKER)).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("one position never reads, removes or retries another position's recording of the same person")
    void recordingsStayWithTheirPosition() throws Exception {
        String recordingId = scheduledRecordingId();
        String otherProject = body(as(consultant, post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"%s\",\"positionTitle\":\"Group COO\"}".formatted(clientId)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        String onOther = body(as(consultant, post("/api/v1/projects/" + otherProject + "/candidates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"Priya Raman","title":"Chief Financial Officer","employerName":"Target Group",
                         "email":"priya@%s"}
                        """.formatted(domain)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        String otherPath = "/api/v1/projects/" + otherProject + "/outreach/candidates/" + onOther + "/notetaker";
        assertThat(jdbc.queryForObject("select count(distinct person_id) from app_lm_project_candidate "
                + "where id in (?::uuid, ?::uuid)", Integer.class, priya, onOther)).isEqualTo(1);

        assertThat(body(as(consultant, get(otherPath)).andExpect(status().isOk()).andReturn()).get("recordings"))
                .isEmpty();
        as(consultant, get(otherPath + "/" + recordingId + "/transcript")).andExpect(status().isNotFound());
        as(consultant, delete(otherPath + "/" + recordingId)).andExpect(status().isNotFound());
        as(consultant, post(otherPath + "/" + recordingId + "/retry")).andExpect(status().isNotFound());
        assertThat(statusOf(recordingId)).isEqualTo("SCHEDULED");
    }

    @Test
    @DisplayName("a call running over its booked end can still be sent a bot, which joins at once, until 20 min past")
    void anOverrunningCallIsStillRecordable() throws Exception {
        Instant now = Instant.now();
        jdbc.update("update app_lm_person_meeting set starts_at = ?, ends_at = ? where id = ?::uuid",
                Timestamp.from(now.minus(Duration.ofMinutes(40))), Timestamp.from(now.minus(Duration.ofMinutes(10))),
                meetingId());

        assertThat(notetaker().get("recordableMeetingIds")).hasSize(1);
        schedule().andExpect(status().isCreated());
        assertThat(recall.sent().getFirst().joinAt()).isNull();

        jdbc.update("update app_lm_meeting_recording set status = 'CANCELLED' where project_id = ?::uuid", projectId);
        jdbc.update("update app_lm_person_meeting set starts_at = ?, ends_at = ? where id = ?::uuid",
                Timestamp.from(now.minus(Duration.ofMinutes(60))), Timestamp.from(now.minus(Duration.ofMinutes(30))),
                meetingId());

        assertThat(notetaker().get("recordableMeetingIds")).isEmpty();
        schedule().andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOTETAKER_MEETING_OVER"));
        assertThat(recall.sent()).hasSize(1);
    }

    @Test
    @DisplayName("past its booked end a call is not offered once the consultant's next call has begun on its link")
    void aReusedRoomIsNotRecordedAsTheEarlierCall() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        jdbc.update("update app_lm_person_meeting set starts_at = ?, ends_at = ? where id = ?::uuid",
                Timestamp.from(now.minus(Duration.ofMinutes(40))), Timestamp.from(now.minus(Duration.ofMinutes(10))),
                meetingId());
        gateway.deliverNext(List.of(new CalendarEventChanged(grantId, new CalendarEvent("call-2",
                "Next interview", now.minus(Duration.ofMinutes(10)), now.plus(Duration.ofMinutes(20)),
                List.of(MAILBOX, "priya@" + domain), "https://meet.google.com/abc-defg-hij", "Google Meet"))));
        mvc.perform(post("/api/v1/outreach/webhooks/mailbox")
                        .header("X-Nylas-Signature", RecordingMailboxGateway.VALID_SIGNATURE)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        assertThat(notetaker().get("recordableMeetingIds")).extracting(JsonNode::asText)
                .doesNotContain(meetingId());
        schedule().andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOTETAKER_MEETING_OVER"));
        assertThat(recall.sent()).isEmpty();
    }

    @Test
    @DisplayName("a call only on a teammate's calendar is not the consultant's to record")
    void aTeammatesCallIsNotRecordable() throws Exception {
        String teammate = jdbc.queryForObject("select id::text from app_lm_user where id <> "
                + "(select user_id from app_lm_person_meeting where id = ?::uuid) limit 1", String.class, meetingId());
        jdbc.update("update app_lm_person_meeting set user_id = ?::uuid, booked_by_user_id = null where id = ?::uuid",
                teammate, meetingId());

        assertThat(notetaker().get("recordableMeetingIds")).isEmpty();
        schedule().andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOTETAKER_NOT_YOUR_CALL"));
        assertThat(recall.sent()).isEmpty();
    }

    @Test
    @DisplayName("a Remove pressed while Recall is still being asked calls the new bot straight back")
    void removedWhileSending() throws Exception {
        recall.whileSending(() -> jdbc.update("update app_lm_meeting_recording set status = 'CANCELLED' "
                + "where status = 'SCHEDULED' and recall_bot_id is null and project_id = ?::uuid", projectId));

        JsonNode scheduled = body(schedule().andExpect(status().isCreated()).andReturn());

        assertThat(scheduled.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(recall.removed()).containsExactly(recall.lastBotId());
    }

    @Test
    @DisplayName("a transcript is cleared once its Meeting note is gone for good")
    void transcriptGoesWithItsNote() throws Exception {
        chat.answerWhenSystemContains(SUMMARY_MARKER, SUMMARY);
        String recordingId = scheduledRecordingId();
        String bot = recall.lastBotId();
        recall.holdsTranscript("t-1", List.of(new TranscriptLine(start, "Priya Raman", "Hello.", true)));
        deliver(bot, """
                {"event":"bot.in_call_recording","data":{"bot":{"id":"%s"},"data":{"code":"in_call_recording"}}}""");
        deliver(bot, """
                {"event":"transcript.done","data":{"bot":{"id":"%s"},"transcript":{"id":"t-1"}}}""");
        assertThat(statusOf(recordingId)).isEqualTo("DONE");

        transcriptPurge.purgeAt(Instant.now());
        assertThat(transcriptHeld(recordingId)).isTrue();

        jdbc.update("delete from app_lm_person_note where id = (select note_id from app_lm_meeting_recording "
                + "where id = ?::uuid)", recordingId);
        transcriptPurge.purgeAt(Instant.now());
        assertThat(transcriptHeld(recordingId)).isFalse();
    }

    @Test
    @DisplayName("no bot goes to someone marked do not contact, to a deployment without Recall, or for a client seat")
    void refusals() throws Exception {
        recall.offered(false);
        schedule().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOTETAKER_NOT_OFFERED"));
        recall.offered(true);

        recall.failSends(true);
        schedule().andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("NOTETAKER_SEND_FAILED"));
        assertThat(notetaker().get("recordings").get(0).get("status").asText()).isEqualTo("FAILED");
        recall.failSends(false);

        String rep = clientSeat();
        as(rep, post(notetakerPath().replace("/notetaker", "/meetings/" + meetingId() + "/notetaker")))
                .andExpect(status().isForbidden());
        as(rep, get(notetakerPath())).andExpect(status().isForbidden());

        markDoNotContact();
        schedule().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MEETING_DO_NOT_CONTACT"));
        assertThat(recall.sent()).isEmpty();
    }

    private ResultActions schedule() throws Exception {
        return as(consultant, post(outreach("/candidates/" + priya + "/meetings/" + meetingId() + "/notetaker")));
    }

    private String scheduledRecordingId() throws Exception {
        return body(schedule().andExpect(status().isCreated()).andReturn()).get("id").asText();
    }

    private void deliver(String bot, String template) throws Exception {
        delivered(bot, template, RecordingRecallBotApi.VALID_SIGNATURE).andExpect(status().isOk());
    }

    private ResultActions delivered(String bot, String template, String signature) throws Exception {
        String body = template.formatted(bot);
        return mvc.perform(post("/api/v1/notetaker/webhooks/recall")
                .header("webhook-id", "msg-1")
                .header("webhook-timestamp", String.valueOf(Instant.now().getEpochSecond()))
                .header("webhook-signature", signature)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private boolean transcriptHeld(String recordingId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select transcript is not null from app_lm_meeting_recording "
                + "where id = ?::uuid", Boolean.class, recordingId));
    }

    private String statusOf(String recordingId) {
        return jdbc.queryForObject("select status from app_lm_meeting_recording where id = ?::uuid", String.class,
                recordingId);
    }

    private JsonNode notetaker() throws Exception {
        return body(as(consultant, get(notetakerPath())).andExpect(status().isOk()).andReturn());
    }

    private String meetingId() {
        return jdbc.queryForObject("select id::text from app_lm_person_meeting where provider_event_id = 'call-1' "
                + "and person_id = (select person_id from app_lm_project_candidate where id = ?::uuid)",
                String.class, priya);
    }

    private String notetakerPath() {
        return outreach("/candidates/" + priya + "/notetaker");
    }

    private String outreach(String path) {
        return "/api/v1/projects/" + projectId + "/outreach" + path;
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

    private void markDoNotContact() throws Exception {
        String personId = body(as(consultant, get("/api/v1/projects/" + projectId + "/candidates/" + priya))
                .andReturn()).get("personId").asText();
        as(consultant, put("/api/v1/candidates/" + personId + "/do-not-contact")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"doNotContact\":true,\"reason\":\"Asked not to be approached.\"}"))
                .andExpect(status().isOk());
    }

    private String mandate() throws Exception {
        clientId = body(as(consultant, post("/api/v1/clients").contentType(MediaType.APPLICATION_JSON)
                .content("{\"customName\":\"Acme Holdings\"}"))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        return body(as(consultant, post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"%s\",\"positionTitle\":\"Group CFO\"}".formatted(clientId)))
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
}
