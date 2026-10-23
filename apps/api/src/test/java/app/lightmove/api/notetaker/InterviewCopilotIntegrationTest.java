package app.lightmove.api.notetaker;

import static org.assertj.core.api.Assertions.assertThat;
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
import app.lightmove.api.notetaker.service.InterviewCoachWorker;
import app.lightmove.api.outreach.model.CalendarEvent;
import app.lightmove.api.outreach.model.CalendarEventChanged;
import app.lightmove.api.outreach.model.GrantedMailbox;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.StreamSupport;
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
 * The interview copilot against a recording Recall: questions prepared for a kept call, a bot sent with the live
 * copilot on, Recall's real-time transcript streamed in through its own webhook bodies, and the coach's readings
 * folded onto what the window already showed.
 */
@IntegrationTest
class InterviewCopilotIntegrationTest extends FlowTestSupport {

    private static final String MAILBOX = "consultant@firm.example";
    private static final String PLAN_MARKER = "THE QUESTIONS.";
    private static final String COACH_MARKER = "THE READING.";
    private static final String PLAN = """
            {"fit":[{"id":"c1","question":"Tell me about a refinancing you led.","listenFor":"A named deal"},
                    {"id":"c1","question":"Who signed off your hedging policy?","listenFor":null},
                    {"id":"c1","question":"A third question on the same competency.","listenFor":null},
                    {"id":"c99","question":"A competency the brief does not have.","listenFor":null}],
             "gaps":[{"gap":"NOTICE","question":"When you think about timing, what notice would you need to give?"},
                     {"gap":"NOTICE","question":"A second notice question."},
                     {"gap":"SHOE_SIZE","question":"Not a gap anyone listed."}],
             "clarify":[{"question":"What drove the move in 2021?","why":"Two short tenures"},
                        {"question":"How did the Riyadh role come about?","why":null},
                        {"question":"A third clarifier.","why":null}]}""";

    @Autowired private RecordingMailboxGateway gateway;
    @Autowired private RecordingRecallBotApi recall;
    @Autowired private StubChatModel chat;
    @Autowired private InterviewCoachWorker coachWorker;
    @Autowired private JdbcTemplate jdbc;

    private String grantId;
    private String consultant;
    private String projectId;
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
        createWorkspace(verifiedUser("Yara Haddad", consultantEmail), "Meridian Copilot");
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
    @DisplayName("Prepare questions drafts a plan the code holds to the brief and the record's gaps, never sending pay")
    void preparesQuestions() throws Exception {
        jdbc.update("update app_lm_person set compensation_currency = 'AED', base_salary = 777777 where id = (select "
                + "person_id from app_lm_project_candidate where id = ?::uuid)", priya);
        JsonNode before = body(as(consultant, get(planPath())).andExpect(status().isOk()).andReturn());
        assertThat(before.get("drafted").asBoolean()).isFalse();
        assertThat(texts(before.get("missing"))).contains("NOTICE").doesNotContain("PACKAGE");
        assertThat(before.get("competencies")).isNotEmpty();
        chat.answerWhenSystemContains(PLAN_MARKER, PLAN);

        JsonNode drafted = body(as(consultant, post(planPath())).andExpect(status().isOk()).andReturn());

        assertThat(drafted.get("drafted").asBoolean()).isTrue();
        JsonNode questions = drafted.get("questions");
        assertThat(questions.valueStream().map(question -> question.get("section").asText()).toList())
                .containsExactly("FIT", "FIT", "GAP", "CLARIFY", "CLARIFY");
        assertThat(questions.get(0).get("competency").asText())
                .isEqualTo(drafted.get("competencies").get(0).get("name").asText());
        assertThat(questions.get(2).get("gap").asText()).isEqualTo("NOTICE");
        assertThat(questions.get(0).get("source").asText()).isEqualTo("AI");
        String sent = chat.lastPrompt().getUserMessage().getText();
        assertThat(sent).contains("NOTICE: their notice period").doesNotContain("777777").doesNotContain("777,777")
                .doesNotContain("priya@");
        assertThat(jdbc.queryForObject("select count(*) from app_lm_usage_event where idempotency_key like "
                + "'interview-plan:%' and project_id = ?::uuid", Integer.class, projectId)).isEqualTo(1);

        String firstId = questions.get(0).get("id").asText();
        JsonNode saved = body(as(consultant, put(planPath()).contentType(MediaType.APPLICATION_JSON).content("""
                {"questions":[
                  {"id":"%s","section":"FIT","panel":"%s","competency":"%s","text":"Walk me through one refinancing.",
                   "listenFor":"A named deal","included":true,"source":"MANUAL"},
                  {"id":"%s","section":"GAP","gap":"NOTICE","text":"What notice would you need?","included":false},
                  {"id":null,"section":"CLARIFY","text":"My own question.","included":true,"source":"AI"}]}
                """.formatted(firstId, questions.get(0).get("panel").asText(),
                questions.get(0).get("competency").asText(), questions.get(2).get("id").asText())))
                .andExpect(status().isOk()).andReturn());
        assertThat(saved.get("questions").valueStream().map(question -> question.get("source").asText()).toList())
                .containsExactly("AI", "AI", "MANUAL");
        assertThat(saved.get("questions").get(0).get("text").asText()).isEqualTo("Walk me through one refinancing.");
        assertThat(saved.get("questions").get(1).get("included").asBoolean()).isFalse();
        assertThat(saved.get("savedByName").asText()).isEqualTo("Yara Haddad");

        JsonNode redrafted = body(as(consultant, post(planPath())).andExpect(status().isOk()).andReturn());
        assertThat(redrafted.get("questions")).hasSize(6);
        assertThat(redrafted.get("questions").get(5).get("text").asText()).isEqualTo("My own question.");
    }

    @Test
    @DisplayName("a plan the model cannot draft is refused gracefully, and nothing is stored or charged")
    void planFailsGracefully() throws Exception {
        chat.answerWhenSystemContains(PLAN_MARKER, "{\"fit\":[],\"gaps\":[],\"clarify\":[]}");
        as(consultant, post(planPath())).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"));
        chat.failWhenSystemContains(PLAN_MARKER, new IllegalStateException("model down"));
        as(consultant, post(planPath())).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"));

        JsonNode after = body(as(consultant, get(planPath())).andExpect(status().isOk()).andReturn());
        assertThat(after.get("drafted").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from app_lm_usage_event where idempotency_key like "
                + "'interview-plan:%' and project_id = ?::uuid", Integer.class, projectId)).isZero();
        as(consultant, put(planPath()).contentType(MediaType.APPLICATION_JSON).content("{\"questions\":[{"
                + "\"section\":\"FIT\",\"text\":\"\",\"included\":true}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a bot sent with the live copilot streams its transcript to us, switched only before it joins")
    void liveCopilotIsChosenBeforeTheBotJoins() throws Exception {
        JsonNode scheduled = body(as(consultant, post(schedulePath()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"liveCopilot\":true}")).andExpect(status().isCreated()).andReturn());
        String recordingId = scheduled.get("id").asText();
        assertThat(scheduled.get("liveCopilot").asBoolean()).isTrue();
        assertThat(scheduled.get("liveStatus").asText()).isEqualTo("LISTENING");
        RecallBotRequest sent = recall.sent().getFirst();
        assertThat(sent.liveTranscriptUrl()).isNotNull();
        assertThat(sent.liveTranscriptUrl().getPath()).isEqualTo("/api/v1/notetaker/webhooks/recall/live");

        JsonNode off = body(switchLive(recordingId, false).andExpect(status().isOk()).andReturn());
        assertThat(off.get("liveCopilot").asBoolean()).isFalse();
        assertThat(recall.liveUpdates()).containsEntry(recall.lastBotId(), URI.create("off:none"));
        switchLive(recordingId, true).andExpect(status().isOk());
        assertThat(recall.liveUpdates().get(recall.lastBotId()).getPath())
                .isEqualTo("/api/v1/notetaker/webhooks/recall/live");

        recall.failUpdates(true);
        switchLive(recordingId, false).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOTETAKER_COPILOT_LOCKED"));
        recall.failUpdates(false);
        deliver(recall.lastBotId(), """
                {"event":"bot.joining_call","data":{"bot":{"id":"%s"},"data":{"code":"joining_call"}}}""");
        switchLive(recordingId, false).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOTETAKER_COPILOT_LOCKED"));
        assertThat(liveCopilotOf(recordingId)).isTrue();
    }

    @Test
    @DisplayName("the live transcript streams in once per utterance, and the coach folds its reading forward only")
    void liveCallIsCoached() throws Exception {
        String recordingId = liveRecording();
        String bot = recall.lastBotId();
        JsonNode plan = draftedPlan();
        String competency = plan.get("competencies").get(0).get("name").asText();
        String noticeQuestion = plan.get("questions").valueStream()
                .filter(question -> question.get("section").asText().equals("GAP")).findFirst().orElseThrow()
                .get("id").asText();
        deliver(bot, """
                {"event":"bot.in_call_recording","data":{"bot":{"id":"%s"},"data":{"code":"in_call_recording"}}}""");
        chat.answerWhenSystemContains(COACH_MARKER, """
                {"coverage":[{"id":"c1","state":"TOUCHED","quote":"We refinanced last year.","at":"00:10"}],
                 "gapsHeard":[],"asked":["q3"],
                 "nudges":[{"question":"Walk me through that refinancing.","reason":"No example yet"},
                           {"question":"What notice would you need?","reason":"Notice not asked"}]}""");

        liveDelivered(bot, partial("p1", "Yara Haddad", "Thanks for", 1.0), "nope")
                .andExpect(status().isUnauthorized());
        liveDeliver(bot, partial("p1", "Yara Haddad", "Thanks for", 1.0));
        JsonNode speaking = copilot(recordingId, 0);
        assertThat(speaking.get("speaking")).hasSize(1);
        assertThat(speaking.get("lines")).isEmpty();

        liveDeliver(bot, finished("p1", "Yara Haddad", "Thanks for making the time.", 1.0));
        liveDeliver(bot, finished("p1", "Yara Haddad", "Thanks for making the time.", 1.0));
        liveDeliver(bot, finished("p2", "Priya Raman", "We refinanced last year.", 9.5));
        liveDeliver(bot, finished("p1", "Yara Haddad", "What notice would you need to give?", 20.0));
        liveDeliver(bot, finished("p2", "Priya Raman", "Three months, contractually.", 25.0));

        JsonNode read = copilot(recordingId, 0);
        assertThat(read.get("speaking")).isEmpty();
        assertThat(read.get("lines").valueStream().map(line -> line.get("text").asText()).toList())
                .containsExactly("Thanks for making the time.", "We refinanced last year.",
                        "What notice would you need to give?", "Three months, contractually.");
        assertThat(read.get("lines").get(0).get("team").asBoolean()).isTrue();
        assertThat(read.get("lines").get(1).get("team").asBoolean()).isFalse();
        long cursor = read.get("lastSeq").asLong();
        assertThat(copilot(recordingId, cursor).get("lines")).isEmpty();

        JsonNode coached = awaitCoached(recordingId, 1);
        JsonNode chip = coached.get("coverage").valueStream()
                .filter(line -> line.get("competency").asText().equals(competency)).findFirst().orElseThrow();
        assertThat(chip.get("state").asText()).isEqualTo("TOUCHED");
        assertThat(chip.get("quote").asText()).isEqualTo("We refinanced last year.");
        assertThat(coached.get("plan").valueStream().filter(question -> question.get("asked").asBoolean())
                .map(question -> question.get("id").asText()).toList()).containsExactly(noticeQuestion);
        assertThat(coached.get("nudges")).hasSize(2);
        String coachSaw = chat.prompts().stream().map(prompt -> prompt.getUserMessage().getText())
                .filter(text -> text.contains("THE TRANSCRIPT SO FAR")).reduce((first, second) -> second)
                .orElseThrow();
        assertThat(coachSaw).contains("Yara Haddad (your team): Thanks for making the time.")
                .contains("Priya Raman: We refinanced last year.");
        assertThat(jdbc.queryForObject("select count(*) from app_lm_usage_event where idempotency_key = ?",
                Integer.class, "interview-copilot:" + recordingId)).isEqualTo(1);

        String dismissed = coached.get("nudges").get(0).get("id").asText();
        as(consultant, post(copilotPath(recordingId) + "/nudges/" + dismissed + "/dismiss"))
                .andExpect(status().isNoContent());
        chat.answerWhenSystemContains(COACH_MARKER, """
                {"coverage":[{"id":"c1","state":"NOT_YET","quote":null,"at":null}],
                 "gapsHeard":[{"gap":"NOTICE","at":"00:25"}],"asked":[],
                 "nudges":[{"question":"Walk me through that refinancing!","reason":"Again"},
                           {"question":"Who reports to you today?","reason":"Team not reached"}]}""");
        liveDeliver(bot, finished("p2", "Priya Raman", "Happy to share more.", 40.0));
        rewindCoach(recordingId);
        assertThat(coachWorker.coachIfDue(java.util.UUID.fromString(recordingId), Instant.now().plusSeconds(60)))
                .isTrue();

        JsonNode again = copilot(recordingId, 0);
        JsonNode stillTouched = again.get("coverage").valueStream()
                .filter(line -> line.get("competency").asText().equals(competency)).findFirst().orElseThrow();
        assertThat(stillTouched.get("state").asText()).isEqualTo("TOUCHED");
        assertThat(again.get("nudges").valueStream().map(nudge -> nudge.get("text").asText()).toList())
                .containsExactly("Who reports to you today?");
        assertThat(again.get("gaps").valueStream().filter(gap -> gap.get("gap").asText().equals("NOTICE"))
                .findFirst().orElseThrow().get("heard").asBoolean()).isTrue();
        assertThat(again.get("plan").valueStream().filter(question -> question.get("asked").asBoolean()).count())
                .isEqualTo(1);

        deliver(bot, """
                {"event":"bot.done","data":{"bot":{"id":"%s"},"data":{"code":"done","sub_code":null}}}""");
        assertThat(jdbc.queryForObject("select count(*) from app_lm_meeting_live_line where recording_id = ?::uuid",
                Integer.class, recordingId)).isZero();
        liveDeliver(bot, finished("p2", "Priya Raman", "Said after the call.", 60.0));
        assertThat(jdbc.queryForObject("select count(*) from app_lm_meeting_live_line where recording_id = ?::uuid",
                Integer.class, recordingId)).isZero();
        assertThat(copilot(recordingId, 0).get("status").asText()).isEqualTo("PROCESSING");
    }

    @Test
    @DisplayName("a failing coach keeps what it showed and pauses, a guarded stretch is skipped, and the cap freezes it")
    void coachFailsGracefully() throws Exception {
        String recordingId = liveRecording();
        String bot = recall.lastBotId();
        deliver(bot, """
                {"event":"bot.in_call_recording","data":{"bot":{"id":"%s"},"data":{"code":"in_call_recording"}}}""");
        chat.failWhenSystemContains(COACH_MARKER, new IllegalStateException("model down"));
        liveDeliver(bot, finished("p2", "Priya Raman", "One.", 1.0));
        java.util.UUID id = java.util.UUID.fromString(recordingId);
        for (int run = 0; run < 3; run++) {
            rewindCoach(recordingId);
            coachWorker.coachIfDue(id, Instant.now().plusSeconds(120));
        }
        assertThat(copilot(recordingId, 0).get("coach").asText()).isEqualTo("PAUSED");
        assertThat(jdbc.queryForObject("select coached_through from app_lm_meeting_recording where id = ?::uuid",
                Long.class, recordingId)).isZero();

        chat.reset();
        chat.answerWhenSystemContains(COACH_MARKER, "{\"guard\":\"" + app.lightmove.api.core.llm.model.BlockedAnswer
                .MARKER + "\"}");
        rewindCoach(recordingId);
        coachWorker.coachIfDue(id, Instant.now().plusSeconds(120));
        assertThat(copilot(recordingId, 0).get("coach").asText()).isEqualTo("GUARDED");
        assertThat(jdbc.queryForObject("select coached_through from app_lm_meeting_recording where id = ?::uuid",
                Long.class, recordingId)).isPositive();

        jdbc.update("update app_lm_meeting_recording set coach_runs = 240 where id = ?::uuid", recordingId);
        liveDeliver(bot, finished("p2", "Priya Raman", "Two.", 5.0));
        rewindCoach(recordingId);
        assertThat(coachWorker.coachIfDue(id, Instant.now().plusSeconds(120))).isFalse();
        assertThat(copilot(recordingId, 0).get("coach").asText()).isEqualTo("CAPPED");
    }

    @Test
    @DisplayName("stopping the copilot is the switcher's; it keeps nothing more of the call and the bot records on")
    void stopsTheCopilot() throws Exception {
        String recordingId = liveRecording();
        String bot = recall.lastBotId();
        deliver(bot, """
                {"event":"bot.in_call_recording","data":{"bot":{"id":"%s"},"data":{"code":"in_call_recording"}}}""");
        liveDeliver(bot, finished("p2", "Priya Raman", "Before the stop.", 1.0));
        as(consultant, get(copilotPath(recordingId) + "/stream")).andExpect(status().isOk());
        JsonNode watching = copilot(recordingId, 0);
        assertThat(watching.get("viewers").valueStream().map(viewer -> viewer.get("name").asText()).toList())
                .containsExactly("Yara Haddad");
        assertThat(watching.get("mayStop").asBoolean()).isTrue();

        JsonNode stopped = body(as(consultant, post(copilotPath(recordingId) + "/stop")).andExpect(status().isOk())
                .andReturn());
        assertThat(stopped.get("liveStatus").asText()).isEqualTo("STOPPED");
        assertThat(stopped.get("stoppedByName").asText()).isEqualTo("Yara Haddad");
        assertThat(stopped.get("lines")).isEmpty();
        liveDeliver(bot, finished("p2", "Priya Raman", "After the stop.", 9.0));
        assertThat(copilot(recordingId, 0).get("lines")).isEmpty();
        assertThat(jdbc.queryForObject("select status from app_lm_meeting_recording where id = ?::uuid",
                String.class, recordingId)).isEqualTo("RECORDING");
    }

    @Test
    @DisplayName("a recording without the copilot has none to read, and a client seat reads nothing of a call")
    void copilotRefusals() throws Exception {
        String recordingId = body(as(consultant, post(schedulePath())).andExpect(status().isCreated()).andReturn())
                .get("id").asText();
        as(consultant, get(copilotPath(recordingId))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOTETAKER_COPILOT_OFF"));
        as(consultant, get(outreach("/candidates/" + priya + "/meetings/" + java.util.UUID.randomUUID() + "/plan")))
                .andExpect(status().isNotFound());
    }

    private String liveRecording() throws Exception {
        return body(as(consultant, post(schedulePath()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"liveCopilot\":true}")).andExpect(status().isCreated()).andReturn()).get("id").asText();
    }

    private JsonNode draftedPlan() throws Exception {
        chat.answerWhenSystemContains(PLAN_MARKER, PLAN);
        return body(as(consultant, post(planPath())).andExpect(status().isOk()).andReturn());
    }

    /** The coach runs off the request thread once enough talk is in; it is waited for, briefly. */
    private JsonNode awaitCoached(String recordingId, int runs) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            Integer done = jdbc.queryForObject("select coach_runs from app_lm_meeting_recording where id = ?::uuid",
                    Integer.class, recordingId);
            if (done != null && done >= runs) {
                return copilot(recordingId, 0);
            }
            Thread.sleep(50);
        }
        throw new AssertionError("The coach never ran for " + recordingId);
    }

    /** As if the last run was long ago, so the next one is not held back by the interval. */
    private void rewindCoach(String recordingId) {
        jdbc.update("update app_lm_meeting_recording set coached_at = now() - interval '1 hour', "
                + "coaching_since = null where id = ?::uuid", recordingId);
    }

    private ResultActions switchLive(String recordingId, boolean enabled) throws Exception {
        return as(consultant, put(copilotPath(recordingId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":" + enabled + "}"));
    }

    private JsonNode copilot(String recordingId, long after) throws Exception {
        return body(as(consultant, get(copilotPath(recordingId)).param("after", String.valueOf(after)))
                .andExpect(status().isOk()).andReturn());
    }

    private boolean liveCopilotOf(String recordingId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select live_copilot from app_lm_meeting_recording "
                + "where id = ?::uuid", Boolean.class, recordingId));
    }

    private static String finished(String participant, String name, String text, double startSeconds) {
        return utterance("transcript.data", participant, name, text, startSeconds);
    }

    private static String partial(String participant, String name, String text, double startSeconds) {
        return utterance("transcript.partial_data", participant, name, text, startSeconds);
    }

    private static String utterance(String event, String participant, String name, String text,
                                    double startSeconds) {
        String words = java.util.Arrays.stream(text.split(" "))
                .map(word -> "{\"text\":\"" + word + "\",\"start_timestamp\":{\"relative\":" + startSeconds
                        + "},\"end_timestamp\":{\"relative\":" + (startSeconds + 2) + "}}")
                .reduce((first, second) -> first + "," + second).orElse("");
        return """
                {"event":"%s","data":{"data":{"words":[%s],"participant":{"id":"%s","name":"%s"}},
                 "bot":{"id":"%%s"}}}""".formatted(event, words, participant, name);
    }

    private void liveDeliver(String bot, String template) throws Exception {
        liveDelivered(bot, template, RecordingRecallBotApi.VALID_SIGNATURE).andExpect(status().isOk());
    }

    private ResultActions liveDelivered(String bot, String template, String signature) throws Exception {
        return mvc.perform(post("/api/v1/notetaker/webhooks/recall/live")
                .header("webhook-id", "msg-live")
                .header("webhook-timestamp", String.valueOf(Instant.now().getEpochSecond()))
                .header("webhook-signature", signature)
                .contentType(MediaType.APPLICATION_JSON).content(template.formatted(bot)));
    }

    private void deliver(String bot, String template) throws Exception {
        mvc.perform(post("/api/v1/notetaker/webhooks/recall")
                        .header("webhook-id", "msg-1")
                        .header("webhook-timestamp", String.valueOf(Instant.now().getEpochSecond()))
                        .header("webhook-signature", RecordingRecallBotApi.VALID_SIGNATURE)
                        .contentType(MediaType.APPLICATION_JSON).content(template.formatted(bot)))
                .andExpect(status().isOk());
    }

    private static List<String> texts(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).toList();
    }

    private String planPath() {
        return outreach("/candidates/" + priya + "/meetings/" + meetingId() + "/plan");
    }

    private String schedulePath() {
        return outreach("/candidates/" + priya + "/meetings/" + meetingId() + "/notetaker");
    }

    private String copilotPath(String recordingId) {
        return outreach("/candidates/" + priya + "/notetaker/" + recordingId + "/copilot");
    }

    private String meetingId() {
        return jdbc.queryForObject("select id::text from app_lm_person_meeting where provider_event_id = 'call-1' "
                + "and person_id = (select person_id from app_lm_project_candidate where id = ?::uuid)",
                String.class, priya);
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
