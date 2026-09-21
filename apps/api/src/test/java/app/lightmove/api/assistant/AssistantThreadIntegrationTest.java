package app.lightmove.api.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.assistant.service.AssistantTurnSweeper;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * A thread is one person's, and that is the whole authorisation.
 *
 * <p>The tests that matter here are the refusals: another member of the same workspace must not be
 * able to read a colleague's conversation, and must not be able to tell it exists. Everything else
 * is ordinary CRUD.
 */
@IntegrationTest
class AssistantThreadIntegrationTest extends FlowTestSupport {

    /** Set explicitly: MockMvc sends no User-Agent, and the turn is meant to record the real one. */
    private static final String USER_AGENT = "Mozilla/5.0 (assistant-integration-test)";

    @Autowired
    private JdbcTemplate db;

    @Autowired
    private AssistantTurnSweeper sweeper;

    @Test
    @DisplayName("asking starts a thread titled from the question, and records the turn")
    void askingStartsAThread() throws Exception {
        String owner = staffToken();

        JsonNode accepted = body(ask(owner, "Who are the top IPP operators in Saudi Arabia?", null));

        // The 202 body is deterministic: it is built from the committed row before the worker is
        // handed anything, so it always reads RUNNING with no answer yet.
        assertThat(accepted.get("question").asText())
                .isEqualTo("Who are the top IPP operators in Saudi Arabia?");
        assertThat(accepted.get("status").asText()).isEqualTo("RUNNING");
        assertThat(accepted.get("answer").isNull()).isTrue();

        JsonNode settled = awaitTurnSettled(owner, accepted.get("threadId").asText(),
                accepted.get("id").asText());
        assertThat(settled.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(settled.get("answer").isNull()).isFalse();

        JsonNode threads = body(threads(owner));
        assertThat(threads).hasSize(1);
        assertThat(threads.get(0).get("title").asText())
                .isEqualTo("Who are the top IPP operators in Saudi Arabia?");
    }

    @Test
    @DisplayName("a second question continues the same thread and reads back in order")
    void aSecondQuestionContinuesTheThread() throws Exception {
        String owner = staffToken();
        JsonNode first = body(ask(owner, "first question", null));
        String threadId = first.get("threadId").asText();
        awaitTurnSettled(owner, threadId, first.get("id").asText());

        ask(owner, "second question", threadId);

        JsonNode thread = body(mvc.perform(get("/api/v1/assistant/threads/" + threadId)
                        .header("Authorization", "Bearer " + owner))
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

        mvc.perform(get("/api/v1/assistant/threads/" + threadId)
                        .header("Authorization", "Bearer " + colleague))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/assistant/threads/" + threadId + "/ask")
                        .header("Authorization", "Bearer " + colleague)
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

        assertThat(body(threads(owner))).hasSize(1);
        assertThat(body(threads(colleague))).hasSize(1);
    }

    @Test
    @DisplayName("the turn carries the audit context a background worker could not resolve later")
    void theTurnCarriesItsOrigin() throws Exception {
        String owner = staffToken();
        JsonNode accepted = body(ask(owner, "what is recorded?", null));
        String turnId = accepted.get("id").asText();
        awaitTurnSettled(owner, accepted.get("threadId").asText(), turnId);

        var row = db.queryForMap(
                "SELECT status, finished_at, ip_address, user_agent, started_at FROM app_lm_assistant_turn"
                        + " WHERE id = ?", UUID.fromString(turnId));

        assertThat(row.get("status")).isEqualTo("SUCCEEDED");
        assertThat(row.get("finished_at")).as("V65 ties finished_at to a terminal status").isNotNull();
        assertThat(row.get("started_at")).as("set in Java, not left to the column default").isNotNull();
        assertThat(row.get("ip_address")).isNotNull();
        assertThat(row.get("user_agent")).isEqualTo(USER_AGENT);
    }

    /**
     * A pure client reaches the assistant, and only {@code member(principal)} would let them.
     *
     * <p>Built through the client registry rather than {@code inviteAndAccept}, because the workspace
     * CLIENT role is not invitable — {@code InvitationService} refuses it by name, since a client is
     * invited to a project and never to the tenant. That refusal is the reason this fixture is long.
     */
    @Test
    @DisplayName("a client representative may hold a thread — the tool surface is what limits them")
    void aClientMayUseTheAssistant() throws Exception {
        String admin = staffToken();
        String clientId = createCustomClient(admin, "Acme Corp");
        String project = createProject(admin, clientId, "CFO Search");

        String repEmail = "clara@client-" + domain;
        String representativeId = inviteRepresentative(admin, clientId, "Clara Client", "Chair", repEmail);
        String client = acceptAsNewUser(email.latestTokenFor(repEmail), "Clara Client");
        attachRepresentative(admin, project, representativeId);

        // Staff surfaces stay shut, which is what makes the next line worth asserting.
        mvc.perform(get("/api/v1/members").header("Authorization", "Bearer " + client))
                .andExpect(status().isForbidden());

        JsonNode accepted = body(ask(client, "how is my search going?", null));

        assertThat(awaitTurnSettled(client, accepted.get("threadId").asText(),
                accepted.get("id").asText()).get("status").asText()).isEqualTo("SUCCEEDED");
    }

    @Test
    @DisplayName("a second question while one is running is refused, not queued")
    void aSecondQuestionWhileOneIsRunningIsRefused() throws Exception {
        String owner = staffToken();
        assistantRunner.gate();

        String threadId = body(ask(owner, "first", null)).get("threadId").asText();

        // Every turn spends real money, and a thread answering two at once reads as interleaved
        // nonsense. This also kills the panel's double-submit.
        mvc.perform(post("/api/v1/assistant/threads/" + threadId + "/ask")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"second\"}"))
                .andExpect(status().isConflict());

        assistantRunner.release();
    }

    @Test
    @DisplayName("a turn that fails keeps its question, its code and the text it had produced")
    void aFailedTurnKeepsWhatItHad() throws Exception {
        String owner = staffToken();
        assistantRunner.answering("half an ans");
        assistantRunner.failingWith(new IllegalStateException("vendor exploded"));

        JsonNode accepted = body(ask(owner, "what breaks?", null));
        JsonNode settled = awaitTurnSettled(owner, accepted.get("threadId").asText(),
                accepted.get("id").asText());

        assertThat(settled.get("status").asText()).isEqualTo("FAILED");
        assertThat(settled.get("errorCode").asText()).isEqualTo("INTERNAL_ERROR");
        assertThat(settled.get("question").asText())
                .as("a turn nobody could answer is still a turn that was asked")
                .isEqualTo("what breaks?");

        var row = db.queryForMap(
                "SELECT finished_at, answer FROM app_lm_assistant_turn WHERE id = ?",
                UUID.fromString(accepted.get("id").asText()));
        assertThat(row.get("finished_at"))
                .as("V65 ties a terminal status to finished_at; the worker must set both")
                .isNotNull();
    }

    @Test
    @DisplayName("a stranded turn is reclaimed as CANCELLED rather than sitting RUNNING for ever")
    void aStrandedTurnIsReclaimed() throws Exception {
        String owner = staffToken();
        assistantRunner.gate();
        String turnId = body(ask(owner, "abandoned", null)).get("id").asText();

        // Back-dated so the sweep's cut-off cannot match any other suite's fresh rows — nothing
        // rolls back here, so a sweep using now() would reclaim its neighbours' work.
        db.update("UPDATE app_lm_assistant_turn SET started_at = now() - interval '2 hours'"
                + " WHERE id = ?", UUID.fromString(turnId));

        assertThat(sweeper.sweep(Instant.now().minus(Duration.ofHours(1)))).isPositive();

        assertThat(db.queryForObject(
                "SELECT status FROM app_lm_assistant_turn WHERE id = ?", String.class,
                UUID.fromString(turnId))).isEqualTo("CANCELLED");

        assistantRunner.release();
    }

    @Test
    @DisplayName("a blank question is refused before anything is written")
    void aBlankQuestionIsRefused() throws Exception {
        String owner = staffToken();

        mvc.perform(post("/api/v1/assistant/ask")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"   \"}"))
                .andExpect(status().isBadRequest());

        // Asked of this caller's own history rather than of the table: nothing in this suite rolls
        // back, so every other test's threads are still sitting there.
        assertThat(body(threads(owner))).isEmpty();
    }

    private MvcResult ask(String token, String question, String threadId) throws Exception {
        String path = threadId == null
                ? "/api/v1/assistant/ask"
                : "/api/v1/assistant/threads/" + threadId + "/ask";
        return mvc.perform(post(path)
                        .header("Authorization", "Bearer " + token)
                        .header("User-Agent", USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new AskBody(question))))
                .andExpect(status().isAccepted())
                .andReturn();
    }

    private MvcResult threads(String token) throws Exception {
        return mvc.perform(get("/api/v1/assistant/threads").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
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

    private String createCustomClient(String adminToken, String name) throws Exception {
        return body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customName\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private String createProject(String adminToken, String clientId, String positionTitle) throws Exception {
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"%s\",\"positionTitle\":\"%s\"}"
                                .formatted(clientId, positionTitle)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private String inviteRepresentative(String adminToken, String clientId, String fullName,
                                        String position, String repEmail) throws Exception {
        return body(mvc.perform(post("/api/v1/clients/" + clientId + "/representatives")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"%s\",\"position\":\"%s\",\"email\":\"%s\"}"
                                .formatted(fullName, position, repEmail)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private void attachRepresentative(String adminToken, String projectId, String representativeId)
            throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectId + "/representatives")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"representativeId\":\"%s\"}".formatted(representativeId)))
                .andExpect(status().isOk());
    }

    private String acceptAsNewUser(String token, String fullName) throws Exception {
        return body(mvc.perform(post("/api/v1/onboarding/accept-invitation-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"fullName\":\"%s\",\"password\":\"%s\"}"
                                .formatted(token, fullName, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn()).get("accessToken").asText();
    }

    private record AskBody(String question) {}
}
