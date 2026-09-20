package app.lightmove.api.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import tools.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/**
 * A thread is one person's, and that is the whole authorisation.
 *
 * <p>The tests that matter here are the refusals: another member of the same workspace must not be
 * able to read a colleague's conversation, and must not be able to tell it exists. Everything else
 * is ordinary CRUD.
 */
@IntegrationTest
class AssistantThreadIntegrationTest extends FlowTestSupport {

    @Autowired
    private JdbcTemplate db;

    @Test
    @DisplayName("asking starts a thread titled from the question, and records the turn")
    void askingStartsAThread() throws Exception {
        String owner = staffToken();

        JsonNode answered = body(ask(owner, "Who are the top IPP operators in Saudi Arabia?", null));

        assertThat(answered.get("question").asText())
                .isEqualTo("Who are the top IPP operators in Saudi Arabia?");
        assertThat(answered.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(answered.get("answer").isNull()).isFalse();

        JsonNode threads = body(mvc.perform(get("/api/v1/assistant/threads").header("Authorization", owner))
                .andExpect(status().isOk()).andReturn());
        assertThat(threads).hasSize(1);
        assertThat(threads.get(0).get("title").asText())
                .isEqualTo("Who are the top IPP operators in Saudi Arabia?");
    }

    @Test
    @DisplayName("a second question continues the same thread and reads back in order")
    void aSecondQuestionContinuesTheThread() throws Exception {
        String owner = staffToken();
        String threadId = body(ask(owner, "first question", null)).get("threadId").asText();

        ask(owner, "second question", threadId);

        JsonNode thread = body(mvc.perform(get("/api/v1/assistant/threads/" + threadId)
                        .header("Authorization", owner))
                .andExpect(status().isOk()).andReturn());

        JsonNode turns = thread.get("turns");
        assertThat(turns).hasSize(2);
        assertThat(turns.get(0).get("question").asText()).isEqualTo("first question");
        assertThat(turns.get(1).get("question").asText()).isEqualTo("second question");
    }

    @Test
    @DisplayName("a colleague's thread is a 404, not a 403 — its existence is the thing being hidden")
    void aColleaguesThreadIsNotFound() throws Exception {
        String owner = staffToken();
        String colleague = secondStaffTokenIn(owner);
        String threadId = body(ask(owner, "my private question", null)).get("threadId").asText();

        mvc.perform(get("/api/v1/assistant/threads/" + threadId).header("Authorization", colleague))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/assistant/threads/" + threadId + "/ask")
                        .header("Authorization", colleague)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"let me in\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("two people in one workspace each see only their own history")
    void historyIsScopedToItsOwner() throws Exception {
        String owner = staffToken();
        String colleague = secondStaffTokenIn(owner);

        ask(owner, "mine", null);
        ask(colleague, "theirs", null);

        assertThat(body(mvc.perform(get("/api/v1/assistant/threads").header("Authorization", owner))
                .andReturn())).hasSize(1);
        assertThat(body(mvc.perform(get("/api/v1/assistant/threads").header("Authorization", colleague))
                .andReturn())).hasSize(1);
    }

    @Test
    @DisplayName("the turn carries the audit context a background worker could not resolve later")
    void theTurnCarriesItsOrigin() throws Exception {
        String owner = staffToken();
        String turnId = body(ask(owner, "what is recorded?", null)).get("id").asText();

        var row = db.queryForMap(
                "SELECT status, finished_at, ip_address, user_agent, started_at FROM app_lm_assistant_turn"
                        + " WHERE id = ?", UUID.fromString(turnId));

        assertThat(row.get("status")).isEqualTo("SUCCEEDED");
        assertThat(row.get("finished_at")).as("V65 ties finished_at to a terminal status").isNotNull();
        assertThat(row.get("started_at")).as("set in Java, not left to the column default").isNotNull();
        assertThat(row.get("ip_address")).isNotNull();
        assertThat(row.get("user_agent")).isNotNull();
    }

    @Test
    @DisplayName("a client representative may hold a thread — the tool surface is what limits them")
    void aClientMayUseTheAssistant() throws Exception {
        String admin = staffToken();
        inviteAndAccept(admin, "Clara Client", "clara@" + domain, "CLIENT");
        String client = login("clara@" + domain);

        JsonNode answered = body(ask(client, "how is my search going?", null));

        assertThat(answered.get("status").asText()).isEqualTo("SUCCEEDED");
    }

    @Test
    @DisplayName("a blank question is refused before anything is written")
    void aBlankQuestionIsRefused() throws Exception {
        String owner = staffToken();

        mvc.perform(post("/api/v1/assistant/ask")
                        .header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"   \"}"))
                .andExpect(status().isBadRequest());

        // Asked of this caller's own history rather than of the table: nothing in this suite rolls
        // back, so every other test's threads are still sitting there.
        assertThat(body(mvc.perform(get("/api/v1/assistant/threads").header("Authorization", owner))
                .andReturn())).isEmpty();
    }

    private MvcResult ask(String token, String question, String threadId) throws Exception {
        String path = threadId == null
                ? "/api/v1/assistant/ask"
                : "/api/v1/assistant/threads/" + threadId + "/ask";
        return mvc.perform(post(path)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new AskBody(question))))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String staffToken() throws Exception {
        String token = verifiedUser("Ada Admin", "ada@" + domain);
        createWorkspace(token, "Assistant Firm");
        return login("ada@" + domain);
    }

    private String secondStaffTokenIn(String adminToken) throws Exception {
        inviteAndAccept(adminToken, "Rob Researcher", "rob@" + domain, "MEMBER");
        return login("rob@" + domain);
    }

    private record AskBody(String question) {}
}
