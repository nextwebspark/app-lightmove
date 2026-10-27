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
import app.lightmove.api.notetaker.service.InterviewCoachWorker;
import app.lightmove.api.notetaker.service.NotetakerReconcile;
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
    @Autowired private NotetakerReconcile reconcile;
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
        assertThat(tokenIn(sent.liveTranscriptUrl())).hasSizeGreaterThan(30);
        assertThat(sent.consentLine()).contains("an AI assistant follows it live");
        assertThat(scheduled.get("mayChangeCopilot").asBoolean()).isTrue();

        JsonNode off = body(switchLive(recordingId, false).andExpect(status().isOk()).andReturn());
        assertThat(off.get("liveCopilot").asBoolean()).isFalse();
        assertThat(recall.liveUpdates()).containsEntry(recall.lastBotId(), URI.create("off:none"));
        assertThat(recall.consentLines().get(recall.lastBotId())).doesNotContain("AI assistant");
        assertThat(jdbc.queryForObject("select live_token_hash from app_lm_meeting_recording where id = ?::uuid",
                String.class, recordingId)).isNull();
        switchLive(recordingId, true).andExpect(status().isOk());
        URI switchedTo = recall.liveUpdates().get(recall.lastBotId());
        assertThat(switchedTo.getPath()).isEqualTo("/api/v1/notetaker/webhooks/recall/live");
        assertThat(tokenIn(switchedTo)).isNotEqualTo(tokenIn(sent.liveTranscriptUrl()));
        assertThat(recall.consentLines().get(recall.lastBotId())).contains("AI assistant");

        // Only the bot's sender switches it: it reconfigures their bot and spends their AI use.
        String sender = jdbc.queryForObject("select requested_by::text from app_lm_meeting_recording "
                + "where id = ?::uuid", String.class, recordingId);
        String someoneElse = jdbc.queryForObject("select id::text from app_lm_user where id <> ?::uuid limit 1",
                String.class, sender);
        jdbc.update("update app_lm_meeting_recording set requested_by = ?::uuid where id = ?::uuid", someoneElse,
                recordingId);
        switchLive(recordingId, false).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOTETAKER_COPILOT_SWITCH_NOT_YOURS"));
        jdbc.update("update app_lm_meeting_recording set requested_by = ?::uuid where id = ?::uuid", sender,
                recordingId);

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
                 "gapsHeard":[],"asked":["q3"]}""");

        liveDelivered(bot, partial("p1", "Yara Haddad", "Thanks for", 1.0), null, "nope")
                .andExpect(status().isUnauthorized());
        liveDelivered(bot, partial("p1", "Yara Haddad", "Thanks for", 1.0), "not-the-token", null)
                .andExpect(status().isUnauthorized());
        // A token feeds its own bot's recording and nothing else.
        liveDelivered("bot-somebody-else", partial("p1", "Yara Haddad", "Thanks for", 1.0), tokenOf(bot), null)
                .andExpect(status().isUnauthorized());
        // Recall's Svix signature, where it signs real-time deliveries, still counts on its own.
        liveDelivered(bot, partial("p1", "Yara Haddad", "Thanks", 0.5), null, RecordingRecallBotApi.VALID_SIGNATURE)
                .andExpect(status().isOk());
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
        assertThat(coached.has("nudges")).isFalse();
        String coachSaw = chat.prompts().stream().map(prompt -> prompt.getUserMessage().getText())
                .filter(text -> text.contains("THE TRANSCRIPT SO FAR")).reduce((first, second) -> second)
                .orElseThrow();
        assertThat(coachSaw).contains("Yara Haddad (your team): Thanks for making the time.")
                .contains("Priya Raman: We refinanced last year.");
        assertThat(jdbc.queryForObject("select count(*) from app_lm_usage_event where idempotency_key = ?",
                Integer.class, "interview-copilot:" + recordingId)).isEqualTo(1);

        chat.answerWhenSystemContains(COACH_MARKER, """
                {"coverage":[{"id":"c1","state":"NOT_YET","quote":null,"at":null}],
                 "gapsHeard":[{"gap":"NOTICE","at":null}],"asked":[]}""");
        liveDeliver(bot, finished("p2", "Priya Raman", "Happy to share more.", 40.0));
        rewindCoach(recordingId);
        assertThat(coachWorker.coachIfDue(java.util.UUID.fromString(recordingId), Instant.now().plusSeconds(60)))
                .isTrue();

        JsonNode again = copilot(recordingId, 0);
        JsonNode stillTouched = again.get("coverage").valueStream()
                .filter(line -> line.get("competency").asText().equals(competency)).findFirst().orElseThrow();
        assertThat(stillTouched.get("state").asText()).isEqualTo("TOUCHED");
        assertThat(again.get("gaps").valueStream().filter(gap -> gap.get("gap").asText().equals("NOTICE"))
                .findFirst().orElseThrow().get("heard").asBoolean()).isTrue();
        assertThat(again.get("plan").valueStream().filter(question -> question.get("asked").asBoolean()).count())
                .isEqualTo(1);

        // The bot's own transcript is the English low-latency stream: the note waits for an accurate one.
        recall.botIs(bot, new app.lightmove.api.notetaker.model.RecallBotState("done", null, Instant.now(),
                "t-stream", "done", "rec-live"));
        deliver(bot, """
                {"event":"transcript.done","data":{"bot":{"id":"%s"},"transcript":{"id":"t-stream"}}}""");
        deliver(bot, """
                {"event":"bot.done","data":{"bot":{"id":"%s"},"data":{"code":"done","sub_code":null}}}""");
        assertThat(jdbc.queryForObject("select count(*) from app_lm_meeting_live_line where recording_id = ?::uuid",
                Integer.class, recordingId)).isZero();
        liveDeliver(bot, finished("p2", "Priya Raman", "Said after the call.", 60.0));
        assertThat(jdbc.queryForObject("select count(*) from app_lm_meeting_live_line where recording_id = ?::uuid",
                Integer.class, recordingId)).isZero();
        assertThat(copilot(recordingId, 0).get("status").asText()).isEqualTo("PROCESSING");
        awaitFinalTranscript(recordingId, "final-rec-live");
        assertThat(recall.transcriptRequests()).containsExactly("rec-live");
        assertThat(jdbc.queryForObject("select transcript_id from app_lm_meeting_recording where id = ?::uuid",
                String.class, recordingId)).isNull();

        deliver(bot, """
                {"event":"transcript.done","data":{"transcript":{"id":"final-rec-live"},"recording":{"id":"rec-live"}}}
                """);
        assertThat(jdbc.queryForObject("select transcript_id from app_lm_meeting_recording where id = ?::uuid",
                String.class, recordingId)).isEqualTo("final-rec-live");
    }

    @Test
    @DisplayName("a live call's final transcript Recall never announced is asked for, and folded, by the reconcile")
    void finalTranscriptIsReconciled() throws Exception {
        String recordingId = liveRecording();
        String bot = recall.lastBotId();
        deliver(bot, """
                {"event":"bot.in_call_recording","data":{"bot":{"id":"%s"},"data":{"code":"in_call_recording"}}}""");
        jdbc.update("update app_lm_meeting_recording set ends_at = now() - interval '2 hours', "
                + "starts_at = now() - interval '3 hours' where id = ?::uuid", recordingId);
        recall.botIs(bot, new app.lightmove.api.notetaker.model.RecallBotState("done", null, Instant.now(),
                "t-stream", "done", "rec-9"));
        reconcile.reconcileAt(Instant.now());
        awaitFinalTranscript(recordingId, "final-rec-9");
        assertThat(jdbc.queryForObject("select transcript_id from app_lm_meeting_recording where id = ?::uuid",
                String.class, recordingId)).isNull();

        recall.transcriptIs("final-rec-9", "done");
        reconcile.reconcileAt(Instant.now().plus(Duration.ofMinutes(10)));
        assertThat(jdbc.queryForObject("select transcript_id from app_lm_meeting_recording where id = ?::uuid",
                String.class, recordingId)).isEqualTo("final-rec-9");
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
        assertThat(usageOf(recordingId)).isZero();

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
        // No run ever answered, so the call cost nothing.
        assertThat(usageOf(recordingId)).isZero();
    }

    private int usageOf(String recordingId) {
        Integer count = jdbc.queryForObject("select count(*) from app_lm_usage_event where idempotency_key = ?",
                Integer.class, "interview-copilot:" + recordingId);
        return count == null ? 0 : count;
    }

    /** Asked of Recall off the request thread, after the commit; waited for, briefly. */
    private void awaitFinalTranscript(String recordingId, String expected) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            String held = jdbc.queryForObject("select final_transcript_id from app_lm_meeting_recording "
                    + "where id = ?::uuid", String.class, recordingId);
            if (expected.equals(held)) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("No final transcript was asked for " + recordingId);
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
    @DisplayName("a bot sent at once keeps the copilot it was sent with, and Recall is never asked to change it")
    void aBotSentAtOnceKeepsItsCopilot() throws Exception {
        String recordingId = liveRecording();
        jdbc.update("update app_lm_meeting_recording set join_at = null where id = ?::uuid", recordingId);

        switchLive(recordingId, false).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOTETAKER_COPILOT_LOCKED"));
        assertThat(recall.liveUpdates()).isEmpty();
        assertThat(liveCopilotOf(recordingId)).isTrue();
    }

    @Test
    @DisplayName("a live copilot call ended early still has its note filed from the accurate transcript")
    void aCopilotCallEndedEarlyAsksForTheAccurateTranscript() throws Exception {
        String recordingId = liveRecording();
        String bot = recall.lastBotId();
        deliver(bot, """
                {"event":"bot.in_call_recording","data":{"bot":{"id":"%s"},"data":{"code":"in_call_recording"}}}""");

        as(consultant, post(outreach("/candidates/" + priya + "/notetaker/" + recordingId + "/end")))
                .andExpect(status().isOk());
        assertThat(recall.left()).containsExactly(bot);
        recall.botIs(bot, new app.lightmove.api.notetaker.model.RecallBotState("done", "bot_received_leave_call",
                Instant.now(), "t-stream", "done", "rec-7"));
        deliver(bot, """
                {"event":"bot.done","data":{"bot":{"id":"%s"},"data":{"code":"done","sub_code":null}}}""");

        awaitFinalTranscript(recordingId, "final-rec-7");
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
        as(consultant, delete(outreach("/candidates/" + priya + "/notetaker/" + recordingId)))
                .andExpect(status().isNoContent());

        String live = liveRecording();
        as(consultant, put(copilotPath(live) + "/plan/q-nobody-planned").contentType(MediaType.APPLICATION_JSON)
                .content("{\"asked\":true}")).andExpect(status().isNotFound());
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
        liveDelivered(bot, template, tokenOf(bot), null).andExpect(status().isOk());
    }

    /** Recall posts a real-time delivery to the URL its bot was given, its token in the query; a signature is optional. */
    private ResultActions liveDelivered(String bot, String template, String token, String signature) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/notetaker/webhooks/recall/live")
                .contentType(MediaType.APPLICATION_JSON).content(template.formatted(bot));
        if (token != null) {
            request.param("t", token);
        }
        if (signature != null) {
            request.header("webhook-id", "msg-live")
                    .header("webhook-timestamp", String.valueOf(Instant.now().getEpochSecond()))
                    .header("webhook-signature", signature);
        }
        return mvc.perform(request);
    }

    /** The token in the live URL the bot was last given: by a switch of its copilot, or when it was sent. */
    private String tokenOf(String bot) {
        URI url = recall.liveUpdates().get(bot);
        if (url == null) {
            url = recall.sent().get(recall.botIds().indexOf(bot)).liveTranscriptUrl();
        }
        return tokenIn(url);
    }

    private static String tokenIn(URI url) {
        return UriComponentsBuilder.fromUri(url).build().getQueryParams().getFirst("t");
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
