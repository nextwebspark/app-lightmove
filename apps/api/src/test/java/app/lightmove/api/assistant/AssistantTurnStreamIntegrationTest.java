package app.lightmove.api.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.assistant.constant.AssistantEventKind;
import app.lightmove.api.assistant.service.AssistantEventAppender;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * A turn's log, and the stream that replays it.
 *
 * <p>The strongest tests here are the deterministic ones: a turn that has already settled has a
 * complete log, so replaying it asserts ordering, seq allocation and framing with no timing at all.
 * The live case then proves the one thing replay cannot — that an append reaches an open socket
 * through {@code pg_notify} and the instance's single {@code LISTEN} connection.
 */
@IntegrationTest
class AssistantTurnStreamIntegrationTest extends FlowTestSupport {

    @Autowired
    private JdbcTemplate db;

    @Autowired
    private AssistantEventAppender appender;

    @Test
    @DisplayName("a settled turn replays its whole log, in order, with seq starting at 1")
    void aSettledTurnReplaysItsWholeLog() throws Exception {
        String owner = staffToken();
        assistantRunner.answering("six ", "companies");
        UUID turnId = ask(owner, "who are the top IPP operators?");

        List<JsonNode> frames = replay(owner, turnId, 0, "turn.finished");

        assertThat(frames.stream().map(frame -> frame.get("kind").asText()))
                .as("one delta per chunk the runner emitted, then the settled answer")
                .containsExactly("turn.started", "message.delta", "message.delta", "answer",
                        "turn.finished");
        assertThat(frames.stream().map(frame -> frame.get("seq").asInt()))
                .as("1..5 with no gaps — a shared transaction must see its own earlier inserts")
                .containsExactly(1, 2, 3, 4, 5);
        assertThat(frames.get(0).get("payload").get("question").asText())
                .isEqualTo("who are the top IPP operators?");
        assertThat(frames.get(3).get("payload").get("text").asText())
                .as("the answer event carries the reassembled text, so a replay need not concatenate")
                .isEqualTo("six companies");
        assertThat(frames.getLast().get("payload").get("status").asText()).isEqualTo("SUCCEEDED");
    }

    @Test
    @DisplayName("afterSeq is a cursor: a reconnect does not resend what it already has")
    void afterSeqIsACursor() throws Exception {
        String owner = staffToken();
        assistantRunner.answering("one chunk");
        UUID turnId = ask(owner, "second look");

        List<JsonNode> frames = replay(owner, turnId, 3, "turn.finished");

        assertThat(frames.stream().map(frame -> frame.get("kind").asText()))
                .as("everything up to and including seq 3 is the client's already")
                .containsExactly("turn.finished");
        assertThat(frames.getFirst().get("seq").asInt()).isEqualTo(4);
    }

    @Test
    @DisplayName("an appended event reaches an open stream through pg_notify")
    void anAppendedEventReachesAnOpenStream() throws Exception {
        String owner = staffToken();
        assistantRunner.answering("one chunk");
        UUID turnId = ask(owner, "live delivery");

        // Opened past the terminal event on purpose: nothing replays, so the stream stays open and
        // whatever arrives next can only have come down the notify path under test.
        MvcResult stream = openStream(streamUrl(turnId, lastSeq(owner, turnId)), owner);
        awaitContent(stream, "connected");

        appender.append(turnId, AssistantEventKind.MESSAGE_DELTA, Map.of("text", "arrived live"));

        awaitContent(stream, "arrived live");
    }

    @Test
    @DisplayName("a payload carrying a quote and a newline survives as a parseable frame")
    void aPayloadWithAQuoteAndNewlineSurvives() throws Exception {
        String owner = staffToken();
        assistantRunner.answering("one chunk");
        UUID turnId = ask(owner, "framing");
        String awkward = "He said \"maybe\",\nthen left.";
        int settledAt = lastSeq(owner, turnId);

        appender.append(turnId, AssistantEventKind.MESSAGE_DELTA, Map.of("text", awkward));

        // The regression this exists for: ProjectStreamRegistry builds its frames by concatenation,
        // which silently yields a frame the browser drops as soon as the payload holds a quote — and
        // a literal newline splits one `data:` line into two.
        JsonNode delivered = replay(owner, turnId, settledAt, "message.delta").get(0);
        assertThat(delivered.get("kind").asText()).isEqualTo("message.delta");
        assertThat(delivered.get("payload").get("text").asText()).isEqualTo(awkward);
    }

    @Test
    @DisplayName("a colleague's turn is a 404 on the stream too, not a 403")
    void aColleaguesTurnIsNotFound() throws Exception {
        String owner = staffToken();
        UUID turnId = ask(owner, "my private question");
        inviteAndAccept(owner, "Rob Researcher", "rob@" + domain, "MEMBER");
        String colleague = login("rob@" + domain);

        mvc.perform(get(streamUrl(turnId, 0)).header("Authorization", "Bearer " + colleague))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a turn in another workspace does not exist either")
    void aTurnInAnotherWorkspaceIsNotFound() throws Exception {
        String owner = staffToken();
        UUID turnId = ask(owner, "not yours");

        String stranger = verifiedUser("Zoe Stranger", "zoe@other-" + domain);
        createWorkspace(stranger, "Other Firm");
        String strangerToken = login("zoe@other-" + domain);

        mvc.perform(get(streamUrl(turnId, 0)).header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("two events cannot share a seq — the unique constraint is the guard, not a retry")
    void twoEventsCannotShareASeq() throws Exception {
        String owner = staffToken();
        UUID turnId = ask(owner, "seq guard");

        assertThatThrownBy(() -> db.update(
                "INSERT INTO app_lm_assistant_event (turn_id, seq, kind) VALUES (?, ?, ?)",
                turnId, 1, "turn.started"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("an event cannot be edited once written — seq is a cursor other readers depend on")
    void anEventCannotBeEdited() throws Exception {
        String owner = staffToken();
        UUID turnId = ask(owner, "immutability");

        assertThatThrownBy(() -> db.update(
                "UPDATE app_lm_assistant_event SET kind = 'tampered' WHERE turn_id = ?", turnId))
                // The trigger raises with ERRCODE insufficient_privilege, so SQLSTATE class 42 lands
                // Spring on BadSqlGrammarException — whose own message is only the SQL it refused.
                // The trigger's own words are on the cause, which is where this has to look.
                .isInstanceOf(BadSqlGrammarException.class)
                .rootCause()
                .hasMessageContaining("immutable once written");
    }

    /** @return the turn id, once the worker has settled it, so its log is complete. */
    private UUID ask(String token, String question) throws Exception {
        MvcResult asked = mvc.perform(post("/api/v1/assistant/ask")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("question", question))))
                .andExpect(status().isAccepted())
                .andReturn();
        JsonNode accepted = body(asked);
        String turnId = accepted.get("id").asText();
        awaitTurnSettled(token, accepted.get("threadId").asText(), turnId);
        return UUID.fromString(turnId);
    }

    /**
     * Every frame the stream delivered, parsed. Excludes the opening {@code connected} frame.
     *
     * <p>{@code lastKind} is what the drain is expected to end on — waiting for a known frame rather
     * than sleeping is what keeps these deterministic, and a cursor past the terminal event ends on
     * something else.
     */
    private List<JsonNode> replay(String token, UUID turnId, int afterSeq, String lastKind)
            throws Exception {
        MvcResult stream = openStream(streamUrl(turnId, afterSeq), token);
        awaitContent(stream, "connected");
        awaitContent(stream, lastKind);
        return framesOf(stream.getResponse().getContentAsString());
    }

    private List<JsonNode> framesOf(String sse) {
        List<JsonNode> frames = new ArrayList<>();
        String[] lines = sse.split("\n");
        boolean assistantFrame = false;
        for (String line : lines) {
            if (line.startsWith("event:")) {
                assistantFrame = line.substring("event:".length()).trim().equals("assistant");
            } else if (assistantFrame && line.startsWith("data:")) {
                frames.add(json.readTree(line.substring("data:".length())));
            }
        }
        return frames;
    }

    /** The seq the settled turn ended on, so a test can open a stream past it without guessing. */
    private int lastSeq(String token, UUID turnId) throws Exception {
        return replay(token, turnId, 0, "turn.finished").getLast().get("seq").asInt();
    }

    private static String streamUrl(UUID turnId, int afterSeq) {
        return "/api/v1/assistant/turns/" + turnId + "/stream?afterSeq=" + afterSeq;
    }

    private String staffToken() throws Exception {
        String token = verifiedUser("Ada Admin", "ada@" + domain);
        createWorkspace(token, "Assistant Firm");
        return login("ada@" + domain);
    }
}
