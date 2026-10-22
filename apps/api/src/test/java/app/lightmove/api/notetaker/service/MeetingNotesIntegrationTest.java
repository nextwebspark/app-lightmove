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
import app.lightmove.api.StubChatModel;
import app.lightmove.api.notetaker.controller.NotetakerRunController;
import app.lightmove.api.notetaker.model.NotetakerLaunch;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

/**
 * Notetaker phase 3 against the stub model: a recorded call becomes one transcript, named from the captions, and one
 * Meeting note against its position's brief, with its timeline line, its bell and its minutes metered.
 */
@IntegrationTest
class MeetingNotesIntegrationTest extends FlowTestSupport {

    private static final String MAILBOX = "consultant@firm.example";
    private static final String HEARD = """
            {"lines":[
              {"offsetSeconds":2.0,"speaker":"Speaker 1","text":"I led the finance transformation in Riyadh, write to priya.private@example.com"},
              {"offsetSeconds":12.0,"speaker":"Speaker 2","text":"What drew you to the group role"}]}""";
    private static final String SUMMARY = """
            {"overview":"Priya walked through her finance transformation work and is open to a move.",
             "keyPoints":["Led the finance transformation in Riyadh"],
             "motivation":"Wants a group-level remit","package":null,"notice":"Three months","mobility":null,
             "concerns":[],"nextSteps":["Yara to send the brief"],
             "competencyEvidence":[{"id":"c1","evidence":"Described leading a capital raise.","at":"00:00:02"}],
             "competenciesNotCovered":[],"briefConcerns":[]}""";

    @Autowired private RecordingMailboxGateway gateway;
    @Autowired private RecordingMeetingRecorder recorder;
    @Autowired private NotetakerScheduler scheduler;
    @Autowired private MeetingNotesProcessor processor;
    @Autowired private StubChatModel chat;
    @Autowired private JdbcTemplate jdbc;

    private String grantId;
    private String consultant;
    private String projectId;
    private String workspaceId;

    @BeforeEach
    void setUp() throws Exception {
        gateway.clear();
        recorder.clear();
        chat.reset();
        chat.answerWhenSystemContains("You transcribe one piece", HEARD);
        chat.answerWhenSystemContains("writing the note of a call", SUMMARY);
        grantId = "grant-" + domain;
        gateway.grant(new GrantedMailbox(grantId, MAILBOX, "google"));
        String consultantEmail = "yara@" + domain;
        createWorkspace(verifiedUser("Yara Haddad", consultantEmail), "Meeting Notes " + domain);
        consultant = login(consultantEmail);
        projectId = mandate("Group CFO");
        workspaceId = jdbc.queryForObject("select workspace_id::text from app_lm_project where id = ?::uuid",
                String.class, projectId);
        jdbc.update("""
                insert into app_lm_position_competency (position_id, sort_order, panel, name, weight, source)
                select position.id, coalesce((select max(sort_order) + 1 from app_lm_position_competency competency
                                              where competency.position_id = position.id), 0),
                       'TECHNICAL', 'Capital markets', 20, 'MANUAL'
                from app_lm_position position where position.project_id = ?::uuid""", projectId);
        connectMailbox();
    }

    @AfterEach
    void tearDown() {
        chat.reset();
    }

    @Test
    @DisplayName("a recorded call yields one transcript named from the captions and one Meeting note on its position")
    void aRecordedCallBecomesOneNote() throws Exception {
        String priya = executive(projectId, "Priya Raman", "priya@" + domain, "priya-raman-" + System.nanoTime());
        Instant start = soon();
        changed(event("call-1", start, "priya@" + domain));
        String run = recordAndLeave(start);

        Map<String, Object> recording = recordingRow(run);
        assertThat(recording.get("status")).isEqualTo("DONE");
        assertThat(recording.get("project_id").toString()).isEqualTo(projectId);
        Map<String, Object> note = jdbc.queryForMap("select id, kind, body_format, project_id, body, author_user_id "
                + "from app_lm_person_note where id = ?::uuid", recording.get("note_id").toString());
        assertThat(note.get("kind")).isEqualTo("MEETING");
        assertThat(note.get("body_format")).isEqualTo("MARKDOWN");
        assertThat(note.get("project_id").toString()).isEqualTo(projectId);
        assertThat(note.get("body").toString())
                .startsWith("Priya walked through")
                .contains("## Against the brief", "**Notice:** Three months", "## Next steps")
                .doesNotContain("Partial");
        String personId = personOf(priya);
        assertThat(jdbc.queryForObject("select count(*) from app_lm_person_activity where person_id = ?::uuid "
                + "and kind = 'MEETING_RECORDED'", Integer.class, personId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from app_lm_notification where workspace_id = ?::uuid "
                + "and kind = 'MEETING_NOTES_READY'", Integer.class, workspaceId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from app_lm_usage_event where workspace_id = ?::uuid "
                + "and kind = 'MEETING_MINUTES'", Integer.class, workspaceId)).isEqualTo(1);
        // Every piece's audio went once its transcript was stored; the captions go with the next pass.
        assertThat(mediaKinds(run)).containsOnly("CAPTIONS");
        scheduler.scheduleAt(Instant.now());
        assertThat(mediaKinds(run)).isEmpty();

        Prompt summary = promptWithSystem("writing the note of a call");
        String told = ((UserMessage) summary.getInstructions().getLast()).getText();
        assertThat(told).contains("Title: Group CFO", "Capital markets", "[email removed]")
                .doesNotContain("priya.private@example.com");

        String meetingId = meetingIdOf(priya);
        JsonNode transcript = body(as(consultant, get(meetings(priya) + "/" + meetingId + "/transcript"))
                .andExpect(status().isOk()).andReturn());
        assertThat(transcript.get("complete").asBoolean()).isTrue();
        assertThat(transcript.get("lines").findValuesAsString("speaker")).contains("Priya Raman", "Yara Haddad");
        assertThat(jdbc.queryForObject("select count(*) from app_lm_audit_event where workspace_id = ?::uuid "
                + "and event_type = 'NOTETAKER_TRANSCRIPT_READ'", Integer.class, workspaceId)).isEqualTo(1);

        // Handed the same run again, nothing new is written.
        processor.work(UUID.fromString(run));
        assertThat(jdbc.queryForObject("select count(*) from app_lm_person_note where person_id = ?::uuid "
                + "and kind = 'MEETING'", Integer.class, personId)).isEqualTo(1);
    }

    @Test
    @DisplayName("a bot that falls silent mid-call still leaves a note, marked partial")
    void aSilentBotLeavesAPartialNote() throws Exception {
        executive(projectId, "Priya Raman", "priya@" + domain, "priya-raman-" + System.nanoTime());
        Instant start = soon();
        changed(event("call-1", start, "priya@" + domain));
        scheduler.scheduleAt(Instant.now());
        NotetakerLaunch launch = launchesHere().getFirst();
        String run = launch.recordingId().toString();
        botCall(run, "status", launch.runToken(), "{\"state\":\"RECORDING\"}").andExpect(status().isNoContent());
        upload(run, "audio", 1, launch.runToken(), new byte[] {1, 2, 3}, start).andExpect(status().isNoContent());

        scheduler.scheduleAt(Instant.now().plus(Duration.ofMinutes(3)));

        Map<String, Object> recording = recordingRow(run);
        assertThat(recording.get("status")).isEqualTo("DONE");
        assertThat(recording.get("recorded_until")).isNotNull();
        assertThat(jdbc.queryForObject("select body from app_lm_person_note where id = ?::uuid", String.class,
                recording.get("note_id").toString())).startsWith("**Partial — recording stopped at ");
    }

    @Test
    @DisplayName("audio is transcribed while the call goes, and a removal takes what was transcribed with it")
    void rollingTranscriptionAndRemoval() throws Exception {
        String priya = executive(projectId, "Priya Raman", "priya@" + domain, "priya-raman-" + System.nanoTime());
        Instant start = soon();
        changed(event("call-1", start, "priya@" + domain));
        scheduler.scheduleAt(Instant.now());
        NotetakerLaunch launch = launchesHere().getFirst();
        String run = launch.recordingId().toString();
        botCall(run, "status", launch.runToken(), "{\"state\":\"RECORDING\"}").andExpect(status().isNoContent());
        upload(run, "audio", 1, launch.runToken(), new byte[] {1, 2, 3}, start).andExpect(status().isNoContent());

        scheduler.scheduleAt(Instant.now());
        assertThat(mediaKinds(run)).containsExactly("AUDIO");
        jdbc.update("update app_lm_meeting_recording_media set received_at = now() - interval '6 minutes' "
                + "where recording_id = ?::uuid", run);
        scheduler.scheduleAt(Instant.now());

        assertThat(recordingRow(run).get("status")).isEqualTo("RECORDING");
        assertThat(mediaKinds(run)).isEmpty();
        assertThat(transcriptPieces(run)).isEqualTo(1);

        as(consultant, post(meetings(priya) + "/" + meetingIdOf(priya) + "/notetaker/remove"))
                .andExpect(status().isNoContent());
        botCall(run, "complete", launch.runToken(), "").andExpect(status().isNoContent());
        scheduler.scheduleAt(Instant.now());
        assertThat(recordingRow(run).get("status")).isEqualTo("REMOVED");
        assertThat(transcriptPieces(run)).isZero();
    }

    @Test
    @DisplayName("a note that cannot be made is tried three times, then waits for Try again")
    void failedNotesAreRetried() throws Exception {
        String priya = executive(projectId, "Priya Raman", "priya@" + domain, "priya-raman-" + System.nanoTime());
        chat.answerWhenSystemContains("writing the note of a call", "{\"keyPoints\":[]}");
        Instant start = soon();
        changed(event("call-1", start, "priya@" + domain));
        String run = recordAndLeave(start);
        assertThat(recordingRow(run).get("status")).isEqualTo("PROCESSING");
        scheduler.scheduleAt(Instant.now());
        scheduler.scheduleAt(Instant.now());
        Map<String, Object> failed = recordingRow(run);
        assertThat(failed.get("status")).isEqualTo("FAILED");
        assertThat(failed.get("end_reason")).isEqualTo("PROCESSING_FAILED");

        chat.answerWhenSystemContains("writing the note of a call", SUMMARY);
        String meetingId = meetingIdOf(priya);
        as(consultant, post(meetings(priya) + "/" + meetingId + "/notetaker/retry")).andExpect(status().isAccepted());

        assertThat(recordingRow(run).get("status")).isEqualTo("DONE");
        assertThat(codeOf(as(consultant, post(meetings(priya) + "/" + meetingId + "/notetaker/retry"))
                .andExpect(status().isConflict()).andReturn())).isEqualTo("NOTETAKER_NOT_RETRYABLE");
    }

    @Test
    @DisplayName("a call booked on a position is noted there; one about two worked positions is noted on the person")
    void thePositionRule() throws Exception {
        String slug = "priya-raman-" + System.nanoTime();
        String priya = executive(projectId, "Priya Raman", "priya@" + domain, slug);
        String secondProject = mandate("Group Treasurer");
        executive(secondProject, "Priya Raman", "priya@" + domain, slug);
        String personId = personOf(priya);

        Instant first = soon();
        changed(event("call-1", first, "priya@" + domain));
        String unbooked = recordAndLeave(first);
        assertThat(recordingRow(unbooked).get("project_id")).isNull();
        assertThat(jdbc.queryForObject("select project_id from app_lm_person_note where id = ?::uuid", UUID.class,
                recordingRow(unbooked).get("note_id").toString())).isNull();

        Instant second = first.plus(Duration.ofHours(2));
        jdbc.update("""
                insert into app_lm_person_activity (workspace_id, person_id, project_id, kind, details)
                values (?::uuid, ?::uuid, ?::uuid, 'MEETING_BOOKED', jsonb_build_object('startsAt', ?::text))""",
                workspaceId, personId, secondProject, second.toString());
        changed(event("call-2", second, "priya@" + domain));
        String booked = recordAndLeave(second);
        assertThat(recordingRow(booked).get("project_id").toString()).isEqualTo(secondProject);
    }

    /** Sends the bot in at {@code start}, records two pieces and their captions, and has it leave. */
    private String recordAndLeave(Instant start) throws Exception {
        scheduler.scheduleAt(start.minus(Duration.ofMinutes(1)));
        NotetakerLaunch launch = launchesHere().getLast();
        String run = launch.recordingId().toString();
        botCall(run, "status", launch.runToken(), "{\"state\":\"RECORDING\"}").andExpect(status().isNoContent());
        upload(run, "audio", 1, launch.runToken(), new byte[] {1, 2, 3}, start).andExpect(status().isNoContent());
        upload(run, "audio", 2, launch.runToken(), new byte[] {4, 5, 6}, start.plusSeconds(30))
                .andExpect(status().isNoContent());
        String captions = """
                {"lines":[
                  {"id":1,"speaker":"Priya Raman","text":"I led the finance transformation in Riyadh","startedAt":"%s","updatedAt":"%s"},
                  {"id":2,"speaker":"Yara Haddad","text":"What drew you to the group role","startedAt":"%s","updatedAt":"%s"}]}"""
                .formatted(start.plusSeconds(1), start.plusSeconds(8), start.plusSeconds(11), start.plusSeconds(15));
        upload(run, "captions", 1, launch.runToken(), captions.getBytes(), null).andExpect(status().isNoContent());
        botCall(run, "complete", launch.runToken(), "").andExpect(status().isNoContent());
        return run;
    }

    private Prompt promptWithSystem(String marker) {
        return chat.prompts().stream()
                .filter(prompt -> prompt.getSystemMessage().getText().contains(marker))
                .reduce((first, second) -> second)
                .orElseThrow();
    }

    private List<NotetakerLaunch> launchesHere() {
        List<UUID> ours = jdbc.queryForList("select id from app_lm_meeting_recording where workspace_id = ?::uuid "
                + "order by created_at", UUID.class, workspaceId);
        return recorder.launched().stream().filter(launch -> ours.contains(launch.recordingId())).toList();
    }

    private Map<String, Object> recordingRow(String runId) {
        return jdbc.queryForMap("select status, end_reason, project_id, note_id, recorded_until "
                + "from app_lm_meeting_recording where id = ?::uuid", runId);
    }

    private List<String> mediaKinds(String runId) {
        return jdbc.queryForList("select kind from app_lm_meeting_recording_media where recording_id = ?::uuid "
                + "order by kind, sequence", String.class, runId);
    }

    private int transcriptPieces(String runId) {
        return jdbc.queryForObject("select count(*) from app_lm_meeting_transcript_piece where recording_id = ?::uuid",
                Integer.class, runId);
    }

    private ResultActions upload(String runId, String kind, int sequence, String token, byte[] content,
                                 Instant startedAt) throws Exception {
        MockHttpServletRequestBuilder request = put(NotetakerRunController.BASE + "/" + runId + "/media/" + kind + "/"
                + sequence)
                .header(NotetakerRunController.RUN_TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(content);
        if (startedAt != null) {
            request.header(NotetakerRunController.PIECE_STARTED_AT_HEADER, startedAt.toString());
        }
        return mvc.perform(request);
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

    private String personOf(String candidateId) throws Exception {
        return body(as(consultant, get("/api/v1/projects/" + projectId + "/candidates/" + candidateId))
                .andReturn()).get("personId").asText();
    }

    private String meetings(String candidateId) {
        return "/api/v1/projects/" + projectId + "/outreach/candidates/" + candidateId + "/meetings";
    }

    private static Instant soon() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(Duration.ofMinutes(1));
    }

    private static CalendarEvent event(String id, Instant start, String... executives) {
        List<String> participants = new java.util.ArrayList<>(List.of(executives));
        participants.add(MAILBOX);
        return new CalendarEvent(id, "Conversation", start, start.plus(Duration.ofMinutes(30)), participants,
                "https://meet.google.com/" + id, "Google Meet");
    }

    private void changed(CalendarEvent event) throws Exception {
        gateway.deliverNext(List.of(new CalendarEventChanged(grantId, event)));
        mvc.perform(post("/api/v1/outreach/webhooks/mailbox")
                        .header("X-Nylas-Signature", RecordingMailboxGateway.VALID_SIGNATURE)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    private String executive(String onProject, String fullName, String emailAddress, String slug) throws Exception {
        return body(as(consultant, post("/api/v1/projects/" + onProject + "/candidates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"%s","title":"Chief Financial Officer","employerName":"Target Group",
                         "linkedinUrl":"https://www.linkedin.com/in/%s","email":"%s"}
                        """.formatted(fullName, slug, emailAddress)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private String mandate(String title) throws Exception {
        String clientId = body(as(consultant, post("/api/v1/clients").contentType(MediaType.APPLICATION_JSON)
                .content("{\"customName\":\"Acme Holdings " + title + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        return body(as(consultant, post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"%s\",\"positionTitle\":\"%s\"}".formatted(clientId, title)))
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
