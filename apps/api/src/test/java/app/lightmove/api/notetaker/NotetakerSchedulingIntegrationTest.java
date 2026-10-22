package app.lightmove.api.notetaker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingMailboxGateway;
import app.lightmove.api.RecordingMeetingRecorder;
import app.lightmove.api.notetaker.controller.NotetakerRunController;
import app.lightmove.api.notetaker.model.NotetakerLaunch;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
 * The Notetaker's phase 1 against a recording recorder: a kept call about to start gets one bot while its three
 * switches are on, the bot reports through its run token alone, and Remove notetaker reaches it on its heartbeat.
 */
@IntegrationTest
class NotetakerSchedulingIntegrationTest extends FlowTestSupport {

    private static final String MAILBOX = "consultant@firm.example";

    @Autowired private RecordingMailboxGateway gateway;
    @Autowired private RecordingMeetingRecorder recorder;
    @Autowired private NotetakerScheduler scheduler;
    @Autowired private JdbcTemplate jdbc;

    private String grantId;
    private String consultant;
    private String projectId;
    private String workspaceId;

    @BeforeEach
    void setUp() throws Exception {
        gateway.clear();
        recorder.clear();
        grantId = "grant-" + domain;
        gateway.grant(new GrantedMailbox(grantId, MAILBOX, "google"));
        String consultantEmail = "yara@" + domain;
        createWorkspace(verifiedUser("Yara Haddad", consultantEmail), "Notetaker Search " + domain);
        consultant = login(consultantEmail);
        projectId = mandate();
        workspaceId = jdbc.queryForObject("select workspace_id::text from app_lm_project where id = ?::uuid",
                String.class, projectId);
        connectMailbox();
    }

    @Test
    @DisplayName("a kept call about to start gets one bot, however many passes and mapped people it has")
    void oneBotPerCall() throws Exception {
        executive("Priya Raman", "priya@" + domain);
        executive("Rajesh Menon", "rajesh@" + domain);
        Instant start = soon();
        changed(event("call-1", start, "priya@" + domain, "rajesh@" + domain));

        scheduler.scheduleAt(Instant.now());
        scheduler.scheduleAt(Instant.now());

        List<NotetakerLaunch> launches = launchesHere();
        assertThat(launches).hasSize(1);
        assertThat(launches.getFirst().joinUrl()).isEqualTo("https://meet.google.com/call-1");
        assertThat(launches.getFirst().botName()).isEqualTo("Uncava Notetaker");
        assertThat(launches.getFirst().toString()).doesNotContain(launches.getFirst().runToken());
        Map<String, Object> row = recordingRow();
        assertThat(row.get("status")).isEqualTo("LAUNCHING");
        assertThat(row.get("platform")).isEqualTo("GOOGLE_MEET");
        assertThat(row.get("external_id")).isNotNull();
        assertThat(row.get("run_token_hash")).isNotEqualTo(launches.getFirst().runToken());
    }

    @Test
    @DisplayName("no bot while the workspace's, the consultant's or the meeting's switch is off")
    void eachSwitchKeepsTheBotOut() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        changed(event("call-1", soon(), "priya@" + domain));
        String meetingId = notetakerOf(priya).get("meetings").get(0).get("meetingId").asText();

        switchWorkspace(false);
        scheduler.scheduleAt(Instant.now());
        switchWorkspace(true);
        switchMine(false);
        scheduler.scheduleAt(Instant.now());
        switchMine(true);
        switchMeeting(priya, meetingId, false).andExpect(status().isNoContent());
        scheduler.scheduleAt(Instant.now());
        assertThat(launchesHere()).isEmpty();
        assertThat(notetakerOf(priya).get("meetings").get(0).get("enabled").asBoolean()).isFalse();

        switchMeeting(priya, meetingId, true).andExpect(status().isNoContent());
        scheduler.scheduleAt(Instant.now());
        assertThat(launchesHere()).hasSize(1);
        JsonNode drawer = notetakerOf(priya);
        assertThat(drawer.get("meetings").get(0).get("status").asText()).isEqualTo("LAUNCHING");
        assertThat(drawer.get("workspaceEnabled").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("a call already under way is not switched, and a Zoom call is not ours to join")
    void startedAndUnsupportedCalls() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        changed(new CalendarEvent("started", "Underway", Instant.now().minus(Duration.ofMinutes(5)),
                Instant.now().plus(Duration.ofMinutes(25)), List.of(MAILBOX, "priya@" + domain),
                "https://meet.google.com/started", "Google Meet"));
        String meetingId = notetakerOf(priya).get("meetings").get(0).get("meetingId").asText();
        assertThat(codeOf(switchMeeting(priya, meetingId, false).andExpect(status().isConflict()).andReturn()))
                .isEqualTo("NOTETAKER_MEETING_STARTED");

        changed(new CalendarEvent("zoom-1", "Zoom call", soon(), soon().plus(Duration.ofMinutes(30)),
                List.of(MAILBOX, "priya@" + domain), "https://us05web.zoom.us/j/123", "Zoom"));
        scheduler.scheduleAt(Instant.now());
        assertThat(launchesHere()).extracting(NotetakerLaunch::joinUrl)
                .containsExactly("https://meet.google.com/started");
    }

    @Test
    @DisplayName("a call with someone marked do not contact is skipped, once, with that reason")
    void doNotContactSkipsTheCall() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        markDoNotContact(priya);
        changed(event("call-1", soon(), "priya@" + domain));

        scheduler.scheduleAt(Instant.now());
        scheduler.scheduleAt(Instant.now());

        assertThat(launchesHere()).isEmpty();
        Map<String, Object> row = recordingRow();
        assertThat(row.get("status")).isEqualTo("SKIPPED");
        assertThat(row.get("end_reason")).isEqualTo("DO_NOT_CONTACT");
    }

    @Test
    @DisplayName("the bot reports through its run token alone, and leaves for transcription")
    void theBotReportsThroughItsToken() throws Exception {
        executive("Priya Raman", "priya@" + domain);
        changed(event("call-1", soon(), "priya@" + domain));
        scheduler.scheduleAt(Instant.now());
        NotetakerLaunch launch = launchesHere().getFirst();
        String run = launch.recordingId().toString();

        MvcResult stream = openStream("/api/v1/projects/" + projectId + "/stream", consultant);
        awaitContent(stream, "event:connected");
        botCall(run, "status", "wrong-token", "{\"state\":\"WAITING_ROOM\"}").andExpect(status().isNotFound());
        botCall(run, "status", launch.runToken(), "{\"state\":\"WAITING_ROOM\"}").andExpect(status().isNoContent());
        assertThat(recordingRow().get("status")).isEqualTo("WAITING_ROOM");
        // The open drawer's chip follows the bot: its position's stream hears every move of its status.
        awaitContent(stream, "\"notetaker\"");
        botCall(run, "status", launch.runToken(), "{\"state\":\"RECORDING\"}").andExpect(status().isNoContent());
        assertThat(body(botCall(run, "heartbeat", launch.runToken(), "{}").andExpect(status().isOk()).andReturn())
                .get("leave").asBoolean()).isFalse();

        upload(run, "audio", 1, launch.runToken(), new byte[] {1, 2, 3}).andExpect(status().isNoContent());
        upload(run, "audio", 2, launch.runToken(), new byte[] {4, 5}).andExpect(status().isNoContent());
        upload(run, "captions", 1, launch.runToken(), "[]".getBytes()).andExpect(status().isNoContent());
        // A retry of a piece that had landed replaces it rather than adding a second.
        upload(run, "audio", 2, launch.runToken(), new byte[] {4, 5, 6}).andExpect(status().isNoContent());
        upload(run, "audio", 3, "wrong-token", new byte[] {7}).andExpect(status().isNotFound());
        upload(run, "video", 1, launch.runToken(), new byte[] {7}).andExpect(status().isNotFound());
        upload(run, "audio", 0, launch.runToken(), new byte[] {7}).andExpect(status().isBadRequest());
        upload(run, "audio", 4, launch.runToken(), new byte[0]).andExpect(status().isBadRequest());
        assertThat(pieceKeys(run)).containsExactly(
                "notetaker/%s/audio-00001.webm".formatted(run), "notetaker/%s/audio-00002.webm".formatted(run),
                "notetaker/%s/captions-00001.json".formatted(run));
        assertThat(jdbc.queryForObject("select size_bytes from app_lm_meeting_recording_media where recording_id = "
                + "?::uuid and kind = 'AUDIO' and sequence = 2", Long.class, run)).isEqualTo(3L);
        botCall(run, "complete", launch.runToken(), "").andExpect(status().isNoContent());

        Map<String, Object> row = recordingRow();
        assertThat(row.get("status")).isEqualTo("PROCESSING");
        assertThat(row.get("run_token_hash")).isNull();
        assertThat(row.get("joined_at")).isNotNull();
        // Its token went with its run: a finished bot changes nothing more.
        botCall(run, "heartbeat", launch.runToken(), "{}").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Remove notetaker reaches the bot on its heartbeat, and nothing it captured is kept")
    void removeReachesTheBot() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        changed(event("call-1", soon(), "priya@" + domain));
        String meetingId = notetakerOf(priya).get("meetings").get(0).get("meetingId").asText();
        assertThat(codeOf(remove(priya, meetingId).andExpect(status().isConflict()).andReturn()))
                .isEqualTo("NOTETAKER_NOT_IN_CALL");

        scheduler.scheduleAt(Instant.now());
        NotetakerLaunch launch = launchesHere().getFirst();
        String run = launch.recordingId().toString();
        botCall(run, "status", launch.runToken(), "{\"state\":\"RECORDING\"}").andExpect(status().isNoContent());
        upload(run, "audio", 1, launch.runToken(), new byte[] {1, 2, 3}).andExpect(status().isNoContent());

        remove(priya, meetingId).andExpect(status().isNoContent());
        assertThat(recorder.askedToLeave()).hasSize(1);
        assertThat(body(botCall(run, "heartbeat", launch.runToken(), "{}").andReturn()).get("leave").asBoolean())
                .isTrue();
        // A piece sent after the bot was asked to leave is dropped unread.
        upload(run, "audio", 2, launch.runToken(), new byte[] {4}).andExpect(status().isNoContent());
        assertThat(pieceKeys(run)).hasSize(1);
        botCall(run, "complete", launch.runToken(), "").andExpect(status().isNoContent());

        Map<String, Object> row = recordingRow();
        assertThat(row.get("status")).isEqualTo("REMOVED");
        // The next pass deletes what it had captured, file and row.
        scheduler.scheduleAt(Instant.now());
        assertThat(pieceKeys(run)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from app_lm_audit_event where event_type = 'NOTETAKER_REMOVED' "
                + "and workspace_id = ?::uuid", Integer.class, workspaceId)).isEqualTo(1);
    }

    @Test
    @DisplayName("a failed bot's pieces are kept to look into, then deleted once the retention has passed")
    void failedPiecesAreKeptThenPurged() throws Exception {
        executive("Priya Raman", "priya@" + domain);
        changed(event("call-1", soon(), "priya@" + domain));
        scheduler.scheduleAt(Instant.now());
        NotetakerLaunch launch = launchesHere().getFirst();
        String run = launch.recordingId().toString();
        botCall(run, "status", launch.runToken(), "{\"state\":\"RECORDING\"}").andExpect(status().isNoContent());
        upload(run, "audio", 1, launch.runToken(), new byte[] {1, 2, 3}).andExpect(status().isNoContent());
        botCall(run, "status", launch.runToken(), "{\"state\":\"FAILED\",\"reason\":\"CONSENT_NOT_SHOWN\"}")
                .andExpect(status().isNoContent());
        assertThat(recordingRow().get("end_reason")).isEqualTo("CONSENT_NOT_SHOWN");

        scheduler.scheduleAt(Instant.now());
        assertThat(pieceKeys(run)).hasSize(1);
        scheduler.scheduleAt(Instant.now().plus(Duration.ofDays(8)));
        assertThat(pieceKeys(run)).isEmpty();
    }

    @Test
    @DisplayName("a link the calendar calls Google Meet is joined only when it is an https link on Meet's own host")
    void onlyMeetsOwnHostIsJoined() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        changed(eventWithLink("lookalike", soon(), "https://meet.google.com.example.net/abc", "priya@" + domain));
        changed(eventWithLink("metadata", soon().plus(Duration.ofMinutes(1)), "http://169.254.169.254/computeMetadata",
                "priya@" + domain));
        changed(eventWithLink("plain", soon().plus(Duration.ofMinutes(2)), "http://meet.google.com/abc-defg-hij",
                "priya@" + domain));

        scheduler.scheduleAt(Instant.now());

        assertThat(launchesHere()).isEmpty();
        assertThat(notetakerOf(priya).get("meetings")).hasSize(3)
                .allMatch(meeting -> !meeting.get("joinable").asBoolean());
    }

    @Test
    @DisplayName("switching a call off after its bot was sent, ahead of the start, sends the bot out too")
    void switchingOffASentBotRemovesIt() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        changed(event("call-1", soon(), "priya@" + domain));
        String meetingId = notetakerOf(priya).get("meetings").get(0).get("meetingId").asText();
        scheduler.scheduleAt(Instant.now());
        NotetakerLaunch launch = launchesHere().getFirst();

        switchMeeting(priya, meetingId, false).andExpect(status().isNoContent());

        assertThat(recordingRow().get("status")).isEqualTo("REMOVE_REQUESTED");
        assertThat(body(botCall(launch.recordingId().toString(), "heartbeat", launch.runToken(), "{}").andReturn())
                .get("leave").asBoolean()).isTrue();
        assertThat(recorder.askedToLeave()).hasSize(1);
    }

    @Test
    @DisplayName("two copies of one call whose links differ only in their query are one call to every switch")
    void copiesOfOneCallShareTheSwitch() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        Instant start = soon();
        changed(eventWithLink("copy-a", start, "https://meet.google.com/shared-room", "priya@" + domain));
        changed(eventWithLink("copy-b", start, "https://meet.google.com/shared-room?authuser=1", "priya@" + domain));
        JsonNode meetings = notetakerOf(priya).get("meetings");
        assertThat(meetings).hasSize(2);

        switchMeeting(priya, meetings.get(0).get("meetingId").asText(), false).andExpect(status().isNoContent());
        assertThat(notetakerOf(priya).get("meetings")).allMatch(meeting -> !meeting.get("enabled").asBoolean());
        switchMeeting(priya, meetings.get(1).get("meetingId").asText(), true).andExpect(status().isNoContent());
        assertThat(notetakerOf(priya).get("meetings")).allMatch(meeting -> meeting.get("enabled").asBoolean());

        scheduler.scheduleAt(Instant.now());
        assertThat(launchesHere()).hasSize(1);
    }

    @Test
    @DisplayName("someone marked do not contact while the bot is in the call sends it out on its next heartbeat")
    void doNotContactMidCallSendsTheBotOut() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        changed(event("call-1", soon(), "priya@" + domain));
        scheduler.scheduleAt(Instant.now());
        NotetakerLaunch launch = launchesHere().getFirst();
        String run = launch.recordingId().toString();
        botCall(run, "status", launch.runToken(), "{\"state\":\"RECORDING\"}").andExpect(status().isNoContent());

        markDoNotContact(priya);

        assertThat(body(botCall(run, "heartbeat", launch.runToken(), "{}").andReturn()).get("leave").asBoolean())
                .isTrue();
        botCall(run, "complete", launch.runToken(), "").andExpect(status().isNoContent());
        assertThat(recordingRow().get("status")).isEqualTo("REMOVED");
    }

    @Test
    @DisplayName("a bot removed before it ever reported is closed as removed once its launch window passes")
    void removedBeforeReportingIsSwept() throws Exception {
        String priya = executive("Priya Raman", "priya@" + domain);
        changed(event("call-1", soon(), "priya@" + domain));
        String meetingId = notetakerOf(priya).get("meetings").get(0).get("meetingId").asText();
        scheduler.scheduleAt(Instant.now());

        remove(priya, meetingId).andExpect(status().isNoContent());
        scheduler.scheduleAt(Instant.now().plus(Duration.ofMinutes(6)));

        Map<String, Object> row = recordingRow();
        assertThat(row.get("status")).isEqualTo("REMOVED");
        assertThat(row.get("run_token_hash")).isNull();
    }

    @Test
    @DisplayName("a bot that never reports, or falls silent, ends as failed; one that cannot be sent fails at once")
    void goneBotsFail() throws Exception {
        executive("Priya Raman", "priya@" + domain);
        changed(event("call-1", soon(), "priya@" + domain));
        scheduler.scheduleAt(Instant.now());
        scheduler.scheduleAt(Instant.now().plus(Duration.ofMinutes(6)));
        assertThat(recordingRow().get("end_reason")).isEqualTo("LAUNCH_TIMEOUT");

        recorder.failLaunches(true);
        changed(event("call-2", soon().plus(Duration.ofMinutes(1)), "priya@" + domain));
        scheduler.scheduleAt(Instant.now());
        assertThat(jdbc.queryForObject("select count(*) from app_lm_meeting_recording where workspace_id = ?::uuid "
                + "and end_reason = 'LAUNCH_FAILED'", Integer.class, workspaceId)).isEqualTo(1);
    }

    @Test
    @DisplayName("the workspace's switch is its admin's, and a bot's report of a server finding is refused")
    void switchesAndReports() throws Exception {
        JsonNode workspace = body(as(consultant, get("/api/v1/workspace/notetaker")).andExpect(status().isOk())
                .andReturn());
        assertThat(workspace.get("offered").asBoolean()).isTrue();
        assertThat(workspace.get("enabled").asBoolean()).isTrue();
        assertThat(workspace.get("platforms")).hasSize(2);
        switchWorkspace(false);
        JsonNode mine = body(as(consultant, get("/api/v1/me/notetaker")).andExpect(status().isOk()).andReturn());
        assertThat(mine.get("workspaceEnabled").asBoolean()).isFalse();
        assertThat(mine.get("enabled").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from app_lm_audit_event where workspace_id = ?::uuid "
                + "and metadata ->> 'section' = 'notetaker'", Integer.class, workspaceId)).isEqualTo(1);
        switchWorkspace(true);

        executive("Priya Raman", "priya@" + domain);
        changed(event("call-1", soon(), "priya@" + domain));
        scheduler.scheduleAt(Instant.now());
        NotetakerLaunch launch = launchesHere().getFirst();
        botCall(launch.recordingId().toString(), "status", launch.runToken(),
                "{\"state\":\"FAILED\",\"reason\":\"HEARTBEAT_LOST\"}").andExpect(status().isBadRequest());
        botCall(launch.recordingId().toString(), "status", launch.runToken(),
                "{\"state\":\"FAILED\",\"reason\":\"NOT_ADMITTED\"}").andExpect(status().isNoContent());
        assertThat(recordingRow().get("end_reason")).isEqualTo("NOT_ADMITTED");
    }

    private List<NotetakerLaunch> launchesHere() {
        Set<UUID> ours = jdbc.queryForList("select id from app_lm_meeting_recording where workspace_id = ?::uuid",
                UUID.class, workspaceId).stream().collect(Collectors.toSet());
        return recorder.launched().stream().filter(launch -> ours.contains(launch.recordingId())).toList();
    }

    private Map<String, Object> recordingRow() {
        return jdbc.queryForMap("select status, end_reason, platform, external_id, run_token_hash, joined_at "
                + "from app_lm_meeting_recording where workspace_id = ?::uuid order by created_at limit 1", workspaceId);
    }

    private List<String> pieceKeys(String runId) {
        return jdbc.queryForList("select storage_key from app_lm_meeting_recording_media where recording_id = ?::uuid "
                + "order by kind, sequence", String.class, runId);
    }

    private ResultActions upload(String runId, String kind, int sequence, String token, byte[] content)
            throws Exception {
        return mvc.perform(put(NotetakerRunController.BASE + "/" + runId + "/media/" + kind + "/" + sequence)
                .header(NotetakerRunController.RUN_TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(content));
    }

    private static Instant soon() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(Duration.ofMinutes(1));
    }

    private static CalendarEvent event(String id, Instant start, String... executives) {
        return eventWithLink(id, start, "https://meet.google.com/" + id, executives);
    }

    private static CalendarEvent eventWithLink(String id, Instant start, String joinUrl, String... executives) {
        List<String> participants = new java.util.ArrayList<>(List.of(executives));
        participants.add(MAILBOX);
        return new CalendarEvent(id, "Conversation", start, start.plus(Duration.ofMinutes(30)), participants,
                joinUrl, "Google Meet");
    }

    private ResultActions botCall(String runId, String action, String token, String content) throws Exception {
        return mvc.perform(post(NotetakerRunController.BASE + "/" + runId + "/" + action)
                .header(NotetakerRunController.RUN_TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON).content(content));
    }

    private JsonNode notetakerOf(String candidateId) throws Exception {
        return body(as(consultant, get(meetings(candidateId) + "/notetaker")).andExpect(status().isOk()).andReturn());
    }

    private ResultActions switchMeeting(String candidateId, String meetingId, boolean enabled) throws Exception {
        return as(consultant, put(meetings(candidateId) + "/" + meetingId + "/notetaker")
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":" + enabled + "}"));
    }

    private ResultActions remove(String candidateId, String meetingId) throws Exception {
        return as(consultant, post(meetings(candidateId) + "/" + meetingId + "/notetaker/remove"));
    }

    private void switchWorkspace(boolean enabled) throws Exception {
        as(consultant, put("/api/v1/workspace/notetaker").contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":" + enabled + "}")).andExpect(status().isOk());
    }

    private void switchMine(boolean enabled) throws Exception {
        as(consultant, put("/api/v1/me/notetaker").contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":" + enabled + "}")).andExpect(status().isOk());
    }

    private String meetings(String candidateId) {
        return "/api/v1/projects/" + projectId + "/outreach/candidates/" + candidateId + "/meetings";
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

    private void markDoNotContact(String candidateId) throws Exception {
        String personId = body(as(consultant, get("/api/v1/projects/" + projectId + "/candidates/" + candidateId))
                .andReturn()).get("personId").asText();
        as(consultant, put("/api/v1/candidates/" + personId + "/do-not-contact")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"doNotContact\":true,\"reason\":\"Asked not to be approached.\"}"))
                .andExpect(status().isOk());
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
