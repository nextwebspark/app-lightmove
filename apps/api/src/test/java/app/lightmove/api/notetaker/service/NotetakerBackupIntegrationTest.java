package app.lightmove.api.notetaker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingMailboxGateway;
import app.lightmove.api.RecordingMeetingRecorder;
import app.lightmove.api.RecordingRecallBotApi;
import app.lightmove.api.RecordingZoomApi;
import app.lightmove.api.StubChatModel;
import app.lightmove.api.notetaker.controller.NotetakerRunController;
import app.lightmove.api.notetaker.model.NotetakerLaunch;
import app.lightmove.api.notetaker.model.RecallBotRequest;
import app.lightmove.api.notetaker.model.TranscriptLine;
import app.lightmove.api.outreach.model.CalendarEvent;
import app.lightmove.api.outreach.model.CalendarEventChanged;
import app.lightmove.api.outreach.model.GrantedMailbox;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
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
 * Notetaker phase 6 (#955) against a recording Recall: the backup recorder takes a call our bot could not start, the
 * rest of one ours dropped mid-way, and every Zoom call, reports by signed webhook, and its transcript becomes the same
 * Meeting note, the gap between the two bots owned up to.
 */
@IntegrationTest
class NotetakerBackupIntegrationTest extends FlowTestSupport {

    private static final String MAILBOX = "consultant@firm.example";
    private static final String HEARD = """
            {"lines":[
              {"offsetSeconds":2.0,"speaker":"Speaker 1","text":"I led the finance transformation in Riyadh"}]}""";
    private static final String SUMMARY = """
            {"overview":"Priya walked through her finance transformation work.",
             "keyPoints":[],"motivation":null,"package":null,"notice":null,"mobility":null,
             "concerns":[],"nextSteps":[],"competencyEvidence":[],"competenciesNotCovered":[],"briefConcerns":[]}""";

    @Autowired private RecordingMailboxGateway gateway;
    @Autowired private RecordingMeetingRecorder recorder;
    @Autowired private RecordingRecallBotApi recall;
    @Autowired private RecordingZoomApi zoom;
    @Autowired private NotetakerScheduler scheduler;
    @Autowired private StubChatModel chat;
    @Autowired private MeterRegistry meters;
    @Autowired private JdbcTemplate jdbc;

    private String grantId;
    private String consultant;
    private String projectId;
    private String workspaceId;

    @BeforeEach
    void setUp() throws Exception {
        gateway.clear();
        recorder.clear();
        recall.clear();
        zoom.clear();
        recall.offer(true);
        chat.reset();
        chat.answerWhenSystemContains("You transcribe one piece", HEARD);
        chat.answerWhenSystemContains("writing the note of a call", SUMMARY);
        grantId = "grant-" + domain;
        gateway.grant(new GrantedMailbox(grantId, MAILBOX, "google"));
        String consultantEmail = "yara@" + domain;
        createWorkspace(verifiedUser("Yara Haddad", consultantEmail), "Backup Notes " + domain);
        consultant = login(consultantEmail);
        projectId = mandate();
        workspaceId = jdbc.queryForObject("select workspace_id::text from app_lm_project where id = ?::uuid",
                String.class, projectId);
        connectMailbox();
        backup(true).andExpect(status().isOk());
    }

    @AfterEach
    void tearDown() {
        recall.clear();
        chat.reset();
    }

    @Test
    @DisplayName("the backup is off until an admin turns it on: until then nothing of a call goes to it")
    void theBackupIsTheWorkspacesChoice() throws Exception {
        backup(false).andExpect(status().isOk());
        JsonNode settings = body(as(consultant, get("/api/v1/workspace/notetaker")).andExpect(status().isOk())
                .andReturn());
        assertThat(settings.get("backupOffered").asBoolean()).isTrue();
        assertThat(settings.get("backupEnabled").asBoolean()).isFalse();
        assertThat(settings.get("platforms")).hasSize(2);
        executive("Priya Raman", "priya@" + domain);
        changed(event("call-1", soon(), "https://meet.google.com/call-1", "Google Meet", "priya@" + domain));
        changed(event("zoom-1", soon(), "https://us05web.zoom.us/j/81234567890", "Zoom", "priya@" + domain));
        recorder.failLaunches(true);

        scheduler.scheduleAt(Instant.now());

        assertThat(recall.sent()).isEmpty();
        assertThat(jdbc.queryForList("select end_reason from app_lm_meeting_recording where workspace_id = ?::uuid",
                String.class, workspaceId)).containsExactly("LAUNCH_FAILED");

        JsonNode on = body(backup(true).andExpect(status().isOk()).andReturn());
        assertThat(on.get("backupEnabled").asBoolean()).isTrue();
        assertThat(on.get("platforms")).hasSize(3);
        assertThat(jdbc.queryForObject("select count(*) from app_lm_audit_event where workspace_id = ?::uuid "
                + "and metadata ->> 'section' = 'notetaker-backup'", Integer.class, workspaceId)).isEqualTo(3);
    }

    @Test
    @DisplayName("a bot of ours that cannot be started is followed by the backup's, whose transcript becomes the note")
    void aLaunchFailureGoesToTheBackup() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        changed(event("call-1", soon(), "https://meet.google.com/call-1", "Google Meet", "priya@" + domain));
        recorder.failLaunches(true);
        double fallbacksBefore = fallbacks("GOOGLE_MEET", "LAUNCH_FAILED");

        scheduler.scheduleAt(Instant.now());

        RecallBotRequest sent = recall.sent().getFirst();
        assertThat(sent.meetingUrl()).isEqualTo("https://meet.google.com/call-1");
        assertThat(sent.consentLine()).startsWith("Uncava Notetaker is recording and transcribing this call");
        assertThat(sent.zoomOnBehalfToken()).isNull();
        Map<String, Object> row = recordingRow();
        String run = row.get("id").toString();
        assertThat(sent.metadata()).containsEntry("recording_id", run).containsEntry("workspace_id", workspaceId);
        assertThat(row.get("recorder")).isEqualTo("RECALL");
        assertThat(row.get("fallback_reason")).isEqualTo("LAUNCH_FAILED");
        assertThat(row.get("external_id")).isEqualTo(recall.lastBotId());
        assertThat(row.get("run_token_hash")).isNull();
        assertThat(fallbacks("GOOGLE_MEET", "LAUNCH_FAILED")).isEqualTo(fallbacksBefore + 1);

        botStatus(recall.lastBotId(), "in_waiting_room").andExpect(status().isOk());
        assertThat(recordingRow().get("status")).isEqualTo("WAITING_ROOM");
        botStatus(recall.lastBotId(), "in_call_recording").andExpect(status().isOk());
        assertThat(recordingRow().get("status")).isEqualTo("RECORDING");
        // Not swept as a silent bot of ours: the backup has no heartbeat.
        scheduler.scheduleAt(Instant.now().plus(Duration.ofMinutes(4)));
        assertThat(recordingRow().get("status")).isEqualTo("RECORDING");

        botStatus(recall.lastBotId(), "done").andExpect(status().isOk());
        assertThat(recordingRow().get("status")).isEqualTo("PROCESSING");
        // The note waits for the backup's transcript: the pass the bot's leaving started files nothing.
        Awaitility.await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(recordingRow().get("working_since")).isNull());
        assertThat(recordingRow().get("status")).isEqualTo("PROCESSING");
        Instant spoken = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        recall.transcriptIs("transcript-1", List.of(
                new TranscriptLine(spoken, "Priya Raman", "I led the finance transformation in Riyadh", true),
                new TranscriptLine(spoken.plusSeconds(9), "Yara Haddad", "What drew you to the group role", true)));
        transcriptDone("transcript-1")
                .andExpect(status().isOk());

        awaitNoted();
        Map<String, Object> done = recordingRow();
        assertThat(jdbc.queryForObject("select body from app_lm_person_note where id = ?::uuid", String.class,
                done.get("note_id").toString())).startsWith("Priya walked through").doesNotContain("Partial");
        assertThat(recall.mediaDeleted()).contains(recall.lastBotId());
        JsonNode transcript = body(as(consultant, get(transcript(priya, run))).andExpect(status().isOk()).andReturn());
        assertThat(transcript.get("lines").findValuesAsString("speaker")).containsExactly("Priya Raman", "Yara Haddad");
        assertThat(transcript.get("lines").get(0).get("named").asBoolean()).isTrue();

        // Delivered again, it changes nothing.
        transcriptDone("transcript-1")
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from app_lm_person_note where workspace_id = ?::uuid "
                + "and kind = 'MEETING'", Integer.class, workspaceId)).isEqualTo(1);
    }

    @Test
    @DisplayName("ours dropping mid-call hands the run to the backup, and the note says where the gap was")
    void aCrashMidCallIsPickedUpByTheBackup() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        changed(event("call-1", soon(), "https://meet.google.com/call-1", "Google Meet", "priya@" + domain));
        scheduler.scheduleAt(Instant.now());
        NotetakerLaunch launch = recorder.launched().getLast();
        String run = launch.recordingId().toString();
        botCall(run, "status", launch.runToken(), "{\"state\":\"RECORDING\"}").andExpect(status().isNoContent());
        upload(run, launch.runToken(), Instant.now().minusSeconds(60)).andExpect(status().isNoContent());

        botCall(run, "status", launch.runToken(), "{\"state\":\"FAILED\",\"reason\":\"CRASHED\"}")
                .andExpect(status().isNoContent());

        Map<String, Object> row = recordingRow();
        assertThat(row.get("recorder")).isEqualTo("RECALL");
        assertThat(row.get("fallback_reason")).isEqualTo("CRASHED");
        assertThat(row.get("status")).isEqualTo("LAUNCHING");
        assertThat(row.get("recorded_until")).isNotNull();
        assertThat(row.get("external_id")).isEqualTo(recall.lastBotId());
        assertThat(recall.sent()).hasSize(1);
        // Our bot's token is spent: a bot of ours still alive changes nothing more.
        botCall(run, "heartbeat", launch.runToken(), "{}").andExpect(status().isNotFound());

        botStatus(recall.lastBotId(), "in_call_recording").andExpect(status().isOk());
        botStatus(recall.lastBotId(), "done").andExpect(status().isOk());
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(transcriptPieces(run)).isEqualTo(1);
            assertThat(recordingRow().get("working_since")).isNull();
        });
        assertThat(recordingRow().get("status")).isEqualTo("PROCESSING");

        Instant resumed = Instant.now().plusSeconds(5).truncatedTo(ChronoUnit.SECONDS);
        recall.transcriptIs("transcript-2", List.of(
                new TranscriptLine(resumed, "Priya Raman", "And the board backed the plan", true)));
        transcriptDone("transcript-2")
                .andExpect(status().isOk());

        awaitNoted();
        assertThat(jdbc.queryForObject("select body from app_lm_person_note where id = ?::uuid", String.class,
                recordingRow().get("note_id").toString()))
                .startsWith("**Partial — recording stopped at ")
                .contains(" and resumed at ", " by the backup recorder**");
        JsonNode transcript = body(as(consultant, get(transcript(priya, run))).andExpect(status().isOk()).andReturn());
        assertThat(transcript.get("lines").findValuesAsString("text"))
                .containsExactly("I led the finance transformation in Riyadh", "And the board backed the plan");
        assertThat(transcript.get("recordedUntil").isNull()).isTrue();
    }

    @Test
    @DisplayName("a backup that never gets in leaves what ours heard as a partial note")
    void aBackupThatNeverGetsInLeavesOursPartial() throws Exception {
        executive("Priya Raman", "priya@" + domain);
        changed(event("call-1", soon(), "https://meet.google.com/call-1", "Google Meet", "priya@" + domain));
        scheduler.scheduleAt(Instant.now());
        NotetakerLaunch launch = recorder.launched().getLast();
        String run = launch.recordingId().toString();
        botCall(run, "status", launch.runToken(), "{\"state\":\"RECORDING\"}").andExpect(status().isNoContent());
        upload(run, launch.runToken(), Instant.now().minusSeconds(60)).andExpect(status().isNoContent());
        botCall(run, "status", launch.runToken(), "{\"state\":\"FAILED\",\"reason\":\"CRASHED\"}")
                .andExpect(status().isNoContent());

        botStatus(recall.lastBotId(), "fatal").andExpect(status().isOk());

        awaitNoted();
        assertThat(jdbc.queryForObject("select body from app_lm_person_note where id = ?::uuid", String.class,
                recordingRow().get("note_id").toString()))
                .startsWith("**Partial — recording stopped at ").doesNotContain("resumed");
    }

    @Test
    @DisplayName("a Zoom call goes to the backup on the consultant's own Zoom, and is skipped without one")
    void zoomCallsGoToTheBackup() throws Exception {
        executive("Priya Raman", "priya@" + domain);
        changed(event("zoom-1", soon(), "https://us05web.zoom.us/j/81234567890?pwd=abc", "Zoom", "priya@" + domain));

        scheduler.scheduleAt(Instant.now());
        assertThat(recall.sent()).isEmpty();
        assertThat(recordingRow().get("status")).isEqualTo("SKIPPED");
        assertThat(recordingRow().get("end_reason")).isEqualTo("ZOOM_NOT_CONNECTED");

        ownZoomApp();
        connectZoom();
        changed(event("zoom-2", soon(), "https://us05web.zoom.us/j/81234567891",
                "Zoom", "priya@" + domain));
        scheduler.scheduleAt(Instant.now());

        assertThat(recorder.launched()).isEmpty();
        RecallBotRequest sent = recall.sent().getFirst();
        assertThat(sent.zoomOnBehalfToken()).isEqualTo("obf-81234567891");
        assertThat(zoom.onBehalfAskedFor()).containsExactly("81234567891");
        assertThat(jdbc.queryForObject("select fallback_reason from app_lm_meeting_recording where workspace_id = "
                + "?::uuid and status = 'LAUNCHING'", String.class, workspaceId)).isNull();
    }

    @Test
    @DisplayName("Remove notetaker sends the backup's bot out, and nothing of the call is kept on either side")
    void removeReachesTheBackup() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        changed(event("call-1", soon(), "https://meet.google.com/call-1", "Google Meet", "priya@" + domain));
        recorder.failLaunches(true);
        scheduler.scheduleAt(Instant.now());
        botStatus(recall.lastBotId(), "in_call_recording").andExpect(status().isOk());

        as(consultant, post(meetings(priya) + "/" + meetingIdOf(priya) + "/notetaker/remove"))
                .andExpect(status().isNoContent());
        assertThat(recall.askedToLeave()).containsExactly(recall.lastBotId());
        botStatus(recall.lastBotId(), "done").andExpect(status().isOk());

        assertThat(recordingRow().get("status")).isEqualTo("REMOVED");
        assertThat(recall.mediaDeleted()).containsExactly(recall.lastBotId());
    }

    @Test
    @DisplayName("an unsigned delivery is refused before anything is read, and one about no bot of ours is ignored")
    void theWebhookIsSigned() throws Exception {
        String body = "{\"event\":\"bot.status_change\",\"data\":{\"bot\":{\"id\":\"recall-bot-unknown\"},"
                + "\"status\":{\"code\":\"done\"}}}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        mvc.perform(post("/api/v1/notetaker/webhooks/recall")
                        .header("webhook-id", "msg-1").header("webhook-timestamp", timestamp)
                        .header("webhook-signature", "v1,bm90IGEgc2lnbmF0dXJl")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/notetaker/webhooks/recall").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        // The older svix-* names carry the same signature.
        mvc.perform(post("/api/v1/notetaker/webhooks/recall")
                        .header("svix-id", "msg-2").header("svix-timestamp", timestamp)
                        .header("svix-signature", RecordingRecallBotApi.sign("msg-2", timestamp, body))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    /** A pass already holding the run when the transcript lands leaves it to the scheduler's next pass. */
    private void awaitNoted() {
        Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            scheduler.scheduleAt(Instant.now());
            assertThat(recordingRow().get("status")).isEqualTo("DONE");
        });
    }

    private ResultActions backup(boolean enabled) throws Exception {
        return as(consultant, put("/api/v1/workspace/notetaker/backup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":" + enabled + "}"));
    }

    private double fallbacks(String platform, String reason) {
        var counter = meters.find(ChainedMeetingRecorder.FALLBACKS).tag("platform", platform).tag("reason", reason)
                .counter();
        return counter == null ? 0 : counter.count();
    }

    private ResultActions botStatus(String botId, String code) throws Exception {
        return deliver("""
                {"event":"bot.status_change","data":{"bot":{"id":"%s"},"status":{"code":"%s","created_at":"%s"}}}"""
                .formatted(botId, code, Instant.now()));
    }

    private ResultActions transcriptDone(String transcriptId) throws Exception {
        return deliver("""
                {"event":"transcript.done","data":{"bot":{"id":"%s"},"transcript":{"id":"%s"}}}"""
                .formatted(recall.lastBotId(), transcriptId));
    }

    private ResultActions deliver(String body) throws Exception {
        String id = "msg-" + UUID.randomUUID();
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        return mvc.perform(post("/api/v1/notetaker/webhooks/recall")
                .header("webhook-id", id).header("webhook-timestamp", timestamp)
                .header("webhook-signature", RecordingRecallBotApi.sign(id, timestamp, body))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Map<String, Object> recordingRow() {
        return jdbc.queryForMap("select id, status, end_reason, recorder, fallback_reason, external_id, run_token_hash, "
                + "recorded_until, note_id, working_since from app_lm_meeting_recording where workspace_id = ?::uuid "
                + "order by created_at desc limit 1", workspaceId);
    }

    private int transcriptPieces(String runId) {
        return jdbc.queryForObject("select count(*) from app_lm_meeting_transcript_piece where recording_id = ?::uuid",
                Integer.class, runId);
    }

    private ResultActions upload(String runId, String token, Instant startedAt) throws Exception {
        return mvc.perform(put(NotetakerRunController.BASE + "/" + runId + "/media/audio/1")
                .header(NotetakerRunController.RUN_TOKEN_HEADER, token)
                .header(NotetakerRunController.PIECE_STARTED_AT_HEADER, startedAt.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(new byte[] {1, 2, 3}));
    }

    private ResultActions botCall(String runId, String action, String token, String content) throws Exception {
        return mvc.perform(post(NotetakerRunController.BASE + "/" + runId + "/" + action)
                .header(NotetakerRunController.RUN_TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON).content(content));
    }

    private String meetingIdOf(String candidateId) throws Exception {
        return body(as(consultant, get(meetings(candidateId) + "/notetaker")).andExpect(status().isOk()).andReturn())
                .get("meetings").get(0).get("meetingId").asText();
    }

    private String transcript(String candidateId, String runId) {
        return "/api/v1/projects/" + projectId + "/outreach/candidates/" + candidateId + "/notetaker/recordings/"
                + runId + "/transcript";
    }

    private String meetings(String candidateId) {
        return "/api/v1/projects/" + projectId + "/outreach/candidates/" + candidateId + "/meetings";
    }

    private static Instant soon() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(Duration.ofMinutes(1));
    }

    private static CalendarEvent event(String id, Instant start, String joinUrl, String provider,
                                       String... executives) {
        List<String> participants = new java.util.ArrayList<>(List.of(executives));
        participants.add(MAILBOX);
        return new CalendarEvent(id, "Conversation", start, start.plus(Duration.ofMinutes(30)), participants,
                joinUrl, provider);
    }

    private void changed(CalendarEvent event) throws Exception {
        gateway.deliverNext(List.of(new CalendarEventChanged(grantId, event)));
        mvc.perform(post("/api/v1/outreach/webhooks/mailbox")
                        .header("X-Nylas-Signature", RecordingMailboxGateway.VALID_SIGNATURE)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
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

    private void ownZoomApp() throws Exception {
        as(consultant, put("/api/v1/workspace/integrations/ZOOM").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"OWN\",\"clientId\":\"backup-zoom\",\"clientSecret\":\"zoom-secret-1\"}"))
                .andExpect(status().isOk());
    }

    private void connectZoom() throws Exception {
        MvcResult started = as(consultant, post("/api/v1/outreach/zoom/connect"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = started.getResponse().getCookie("lm_zoom_connect");
        mvc.perform(get("/api/v1/outreach/zoom/callback").param("state", stateOf(started)).param("code", "zoom-code-1")
                        .cookie(cookie))
                .andExpect(status().isFound());
    }

    private void connectMailbox() throws Exception {
        MvcResult started = as(consultant, post("/api/v1/outreach/mailbox/connect")
                .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"google\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = started.getResponse().getCookie("lm_mailbox_connect");
        mvc.perform(get("/api/v1/outreach/mailbox/callback").param("state", stateOf(started)).param("code", "code-1")
                        .cookie(cookie))
                .andExpect(status().isFound());
    }

    private String stateOf(MvcResult started) throws Exception {
        URI authorization = URI.create(body(started).get("authorizationUrl").asText());
        return UriComponentsBuilder.fromUri(authorization).build().getQueryParams().getFirst("state");
    }

    private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }
}
