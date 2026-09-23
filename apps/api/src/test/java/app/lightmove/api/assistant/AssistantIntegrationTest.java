package app.lightmove.api.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.ApolloUniverse;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

/**
 * The assistant's four endpoints against real membership rows. The test model answers with fixed
 * text and calls no tool, so a card is written onto a turn directly where a test needs one.
 */
@IntegrationTest
class AssistantIntegrationTest extends FlowTestSupport {

    @Autowired
    private JdbcTemplate db;

    private ApolloUniverse universe;

    @BeforeEach
    void freshUniverse() {
        universe = new ApolloUniverse(db);
        universe.reset();
    }

    @Test
    @DisplayName("asking starts a chat, a follow-up joins it, and the project's history lists it once")
    void asksAndContinuesAChat() throws Exception {
        Firm firm = firm("Assistant Chat Firm");

        JsonNode first = askAndAwait(firm.admin, firm.projectId, null, "Top retailers in UAE");
        assertThat(first.get("answer").asText()).isEqualTo("stubbed response");
        assertThat(first.get("steps").isArray()).isTrue();
        String threadId = first.get("threadId").asText();

        JsonNode second = askAndAwait(firm.admin, firm.projectId, threadId, "And in Qatar?");
        assertThat(second.get("threadId").asText()).isEqualTo(threadId);

        mvc.perform(get("/api/v1/projects/" + firm.projectId + "/assistant/threads")
                        .header("Authorization", "Bearer " + firm.admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Top retailers in UAE"));

        mvc.perform(get("/api/v1/assistant/threads/" + threadId)
                        .header("Authorization", "Bearer " + firm.admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turns.length()").value(2))
                .andExpect(jsonPath("$.turns[1].question").value("And in Qatar?"));
    }

    @Test
    @DisplayName("a member with no seat on the project cannot ask or list its chats")
    void refusesAnUnseatedMember() throws Exception {
        Firm firm = firm("Assistant Unseated Firm");
        String sara = login(firm.saraEmail);

        mvc.perform(ask(sara, firm.projectId, null, "Top retailers"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/projects/" + firm.projectId + "/assistant/threads")
                        .header("Authorization", "Bearer " + sara))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("someone else's chat is not found, and a chat cannot be continued from another project")
    void keepsChatsPrivateAndInTheirProject() throws Exception {
        Firm firm = firm("Assistant Private Firm");
        String threadId = askAndAwait(firm.admin, firm.projectId, null, "Top retailers")
                .get("threadId").asText();

        mvc.perform(get("/api/v1/assistant/threads/" + threadId)
                        .header("Authorization", "Bearer " + login(firm.saraEmail)))
                .andExpect(status().isNotFound());

        String otherProject = project(firm.admin);
        mvc.perform(ask(firm.admin, otherProject, threadId, "Continue here"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("filing a card writes the ticked companies once, badged as the assistant's")
    void filesTheTickedCompaniesOnce() throws Exception {
        Firm firm = firm("Assistant Accept Firm");
        universe.company("a1", "ACWA Power").industry("oil & energy").employees(4_000).insert();
        universe.company("a2", "Marafiq").industry("oil & energy").employees(2_400).insert();
        String turnId = turnWithCard(firm);

        mvc.perform(accept(firm.admin, turnId, """
                        {"apolloAccountIds":["a1"],"status":"shortlisted"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(1));

        assertThat(db.queryForMap("SELECT company_name, source, status FROM app_lm_project_triage_company"
                + " WHERE project_id = ?", UUID.fromString(firm.projectId)))
                .containsEntry("company_name", "ACWA Power")
                .containsEntry("source", "ASSISTANT")
                .containsEntry("status", "SHORTLISTED");

        mvc.perform(accept(firm.admin, turnId, """
                        {"apolloAccountIds":["a2"]}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSISTANT_PROPOSAL_ALREADY_ACCEPTED"));
    }

    @Test
    @DisplayName("a company the card never offered cannot be filed through it")
    void refusesACompanyTheCardDidNotOffer() throws Exception {
        Firm firm = firm("Assistant Offer Firm");
        universe.company("a1", "ACWA Power").employees(4_000).insert();
        universe.company("a9", "Not Offered").employees(100).insert();
        String turnId = turnWithCard(firm);

        mvc.perform(accept(firm.admin, turnId, """
                        {"apolloAccountIds":["a9"]}"""))
                .andExpect(status().isBadRequest());
    }

    private String turnWithCard(Firm firm) throws Exception {
        String turnId = askAndAwait(firm.admin, firm.projectId, null, "Top utilities")
                .get("id").asText();
        db.update("UPDATE app_lm_assistant_turn SET proposal = ?::jsonb WHERE id = ?", """
                {"title":"Two utilities","companies":[
                  {"apolloAccountId":"a1","companyName":"ACWA Power","country":"Saudi Arabia"},
                  {"apolloAccountId":"a2","companyName":"Marafiq","country":"Saudi Arabia"}]}""",
                UUID.fromString(turnId));
        return turnId;
    }

    /** The answer streams: waits for its {@code done} event and hands back the saved turn. */
    private JsonNode askAndAwait(String token, String projectId, String threadId, String question)
            throws Exception {
        MvcResult stream = mvc.perform(ask(token, projectId, threadId, question))
                .andExpect(request().asyncStarted())
                .andReturn();
        awaitContent(stream, "event:done");
        String content = stream.getResponse().getContentAsString();
        String afterDone = content.substring(content.indexOf("event:done"));
        String data = afterDone.lines().filter(line -> line.startsWith("data:")).findFirst().orElseThrow();
        return json.readTree(data.substring("data:".length()));
    }

    private MockHttpServletRequestBuilder ask(String token, String projectId, String threadId,
                                              String question) {
        String thread = threadId == null ? "" : ",\"threadId\":\"" + threadId + "\"";
        return post("/api/v1/projects/" + projectId + "/assistant/ask")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"" + question + "\"" + thread + "}");
    }

    private MockHttpServletRequestBuilder accept(String token, String turnId, String json) {
        return post("/api/v1/assistant/turns/" + turnId + "/accept")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    private record Firm(String admin, String projectId, String saraEmail) {}

    private Firm firm(String firmName) throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), firmName);
        String admin = login(alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");
        return new Firm(admin, project(admin), sara);
    }

    private String project(String admin) throws Exception {
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Client %s"}""".formatted(UUID.randomUUID())))
                .andReturn()).get("id").asText();
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"Head of Retail"}
                                """.formatted(clientId)))
                .andReturn()).get("id").asText();
    }
}
