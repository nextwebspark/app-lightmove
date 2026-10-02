package app.lightmove.api.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.ApolloUniverse;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.StubChatModel;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.model.MandateStages;
import app.lightmove.api.triagecompany.service.TriageCompanyReadService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
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

    private static final Pattern COMPLETE_DONE_EVENT = Pattern.compile("event:done\\ndata:(.+)\\n\\n");

    @Autowired
    private JdbcTemplate db;

    private ApolloUniverse universe;

    @Autowired
    private StubChatModel model;

    @Autowired
    private TriageCompanyReadService triageReads;

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
    @DisplayName("a firm with no sector on record is offered retail's starters, and only on a seated project")
    void offersRetailStartersToAFirmWithNoSector() throws Exception {
        Firm firm = firm("Assistant Starters Firm");

        mvc.perform(get("/api/v1/projects/" + firm.projectId + "/assistant/starters")
                        .header("Authorization", "Bearer " + firm.admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectorAssumed").value(true))
                .andExpect(jsonPath("$.starters[0].kind").value("SECTOR"))
                .andExpect(jsonPath("$.starters[0].prompt").value("Top 10 Retail companies"))
                .andExpect(jsonPath("$.starters[1].kind").value("ADJACENT"));

        mvc.perform(get("/api/v1/projects/" + firm.projectId + "/assistant/starters")
                        .header("Authorization", "Bearer " + login(firm.saraEmail)))
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
                        {"companyIds":["a1"],"status":"shortlisted"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(1));

        assertThat(db.queryForMap("SELECT company_name, source, status FROM app_lm_project_triage_company"
                + " WHERE project_id = ?", UUID.fromString(firm.projectId)))
                .containsEntry("company_name", "ACWA Power")
                .containsEntry("source", "ASSISTANT")
                .containsEntry("status", "SHORTLISTED");

        mvc.perform(accept(firm.admin, turnId, """
                        {"companyIds":["a2"]}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSISTANT_PROPOSAL_ALREADY_ACCEPTED"));
    }

    @Test
    @DisplayName("a follow-up carries the earlier card, by key, with what was filed from it")
    void replaysTheEarlierCardToTheModel() throws Exception {
        Firm firm = firm("Assistant Memory Firm");
        universe.company("a1", "ACWA Power").employees(4_000).insert();
        universe.company("a2", "Marafiq").employees(2_400).insert();
        String turnId = turnWithCard(firm);
        mvc.perform(accept(firm.admin, turnId, """
                        {"companyIds":["a1"],"status":"shortlisted"}"""))
                .andExpect(status().isOk());
        String threadId = db.queryForObject("SELECT thread_id FROM app_lm_assistant_turn WHERE id = ?",
                UUID.class, UUID.fromString(turnId)).toString();

        askAndAwait(firm.admin, firm.projectId, threadId, "Shortlist the other one too");

        assertThat(model.lastPrompt().getInstructions())
                .filteredOn(message -> message.getMessageType() == MessageType.ASSISTANT)
                .singleElement()
                .extracting(Message::getText)
                .asString()
                .startsWith("stubbed response")
                .contains("<card title=\"Two utilities\">")
                .contains("- [new] a1 · ACWA Power · Saudi Arabia")
                .contains("- [new] a2 · Marafiq · Saudi Arabia")
                .contains("Filed 1 as Shortlisted");
    }

    @Test
    @DisplayName("a company the card showed as already filed keeps its stage when the card is filed")
    void leavesAHeldCompanyWhereItStands() throws Exception {
        Firm firm = firm("Assistant Held Firm");
        universe.company("a1", "ACWA Power").employees(4_000).insert();
        universe.company("a2", "Marafiq").employees(2_400).insert();
        mvc.perform(accept(firm.admin, turnWithCard(firm), """
                        {"companyIds":["a2"],"status":"declined"}"""))
                .andExpect(status().isOk());
        UUID projectId = UUID.fromString(firm.projectId);
        UUID workspaceId = db.queryForObject("SELECT workspace_id FROM app_lm_project WHERE id = ?",
                UUID.class, projectId);

        MandateStages stages = triageReads.stagesOf(workspaceId, projectId, List.of("a1", "a2"),
                List.of("MARAFIQ", "Unknown Co"));
        assertThat(stages.byAccountId()).containsExactly(Map.entry("a2", TriageCompanyStatus.DECLINED));
        assertThat(stages.stageOf(null, "marafiq")).isEqualTo(TriageCompanyStatus.DECLINED);

        String turnId = askAndAwait(firm.admin, firm.projectId, null, "Top utilities again").get("id").asText();
        db.update("UPDATE app_lm_assistant_turn SET proposal = ?::jsonb WHERE id = ?", """
                {"title":"Two utilities","companies":[
                  {"apolloAccountId":"a1","companyName":"ACWA Power","country":"Saudi Arabia"},
                  {"apolloAccountId":"a2","companyName":"Marafiq","country":"Saudi Arabia","stage":"declined"}]}""",
                UUID.fromString(turnId));

        mvc.perform(get("/api/v1/assistant/threads/" + db.queryForObject(
                        "SELECT thread_id FROM app_lm_assistant_turn WHERE id = ?", UUID.class, UUID.fromString(turnId)))
                        .header("Authorization", "Bearer " + firm.admin))
                .andExpect(jsonPath("$.turns[0].proposal.companies[1].stage").value("declined"));

        mvc.perform(accept(firm.admin, turnId, """
                        {"companyIds":["a1","a2"],"status":"shortlisted"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(1))
                .andExpect(jsonPath("$.skipped").value(1));
        assertThat(db.queryForObject("SELECT status FROM app_lm_project_triage_company"
                + " WHERE project_id = ? AND apollo_account_id = 'a2'", String.class, projectId))
                .isEqualTo("DECLINED");
    }

    @Test
    @DisplayName("a company the card never offered cannot be filed through it")
    void refusesACompanyTheCardDidNotOffer() throws Exception {
        Firm firm = firm("Assistant Offer Firm");
        universe.company("a1", "ACWA Power").employees(4_000).insert();
        universe.company("a9", "Not Offered").employees(100).insert();
        String turnId = turnWithCard(firm);

        mvc.perform(accept(firm.admin, turnId, """
                        {"companyIds":["a9"]}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a company researched on LinkedIn is filed whole, with what its page said")
    void filesAResearchedCompany() throws Exception {
        Firm firm = firm("Assistant Researched Firm");
        String turnId = askAndAwait(firm.admin, firm.projectId, null, "Global retailers in UAE")
                .get("id").asText();
        db.update("UPDATE app_lm_assistant_turn SET proposal = ?::jsonb WHERE id = ?", """
                {"title":"Global retailers","companies":[
                  {"linkedinSlug":"ikea","companyName":"IKEA","country":"Sweden","employees":160000,
                   "operates":"IKEA"}],
                 "researched":{"ikea":{"companyName":"IKEA","industry":"Retail","companyCountry":"Sweden",
                   "companyCity":"Delft","numEmployees":160000,"website":"https://www.ikea.com",
                   "companyLinkedinUrl":"https://www.linkedin.com/company/ikea","foundedYear":1943}}}""",
                UUID.fromString(turnId));

        mvc.perform(accept(firm.admin, turnId, """
                        {"companyIds":["ikea"],"status":"shortlisted"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(1));

        assertThat(db.queryForMap("SELECT company_name, source, status, num_employees, website"
                + " FROM app_lm_project_triage_company WHERE project_id = ?", UUID.fromString(firm.projectId)))
                .containsEntry("company_name", "IKEA")
                .containsEntry("source", "ASSISTANT")
                .containsEntry("status", "SHORTLISTED")
                .containsEntry("num_employees", 160000)
                .containsEntry("website", "https://www.ikea.com");
    }

    @Test
    @DisplayName("every question carries the firm: its name and the persona its admins recorded")
    void tellsTheModelAboutTheFirm() throws Exception {
        Firm firm = firm("Assistant Persona Firm");
        mvc.perform(put("/api/v1/workspace/persona")
                        .header("Authorization", "Bearer " + firm.admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"summary":"Gulf retail and property group","sectors":["Retail","Real Estate"],
                                 "competitors":["Majid Al Futtaim"],"geographies":["UAE"]}"""))
                .andExpect(status().isOk());

        askAndAwait(firm.admin, firm.projectId, null, "Top retailers in UAE");

        String system = model.lastPrompt().getSystemMessage().getText();
        assertThat(system)
                .contains("- Name: Assistant Persona Firm")
                .contains("- Sectors: Retail, Real Estate")
                .contains("- Competitors: Majid Al Futtaim")
                .contains("departments or business units")
                .doesNotContain("{hiring}");
    }

    @Test
    @DisplayName("at an agency, every question carries the mandate's client and its persona, not the agency's")
    void tellsTheModelAboutTheClient() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Gulf Search Partners", "AGENCY");
        String admin = login(alok);
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Harbour Health","hqCountry":"Saudi Arabia"}"""))
                .andReturn()).get("id").asText();
        mvc.perform(put("/api/v1/clients/" + clientId + "/persona")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"summary":"Private hospital operator","sectors":["Hospitals"],
                                 "competitors":["Dallah Health"]}"""))
                .andExpect(status().isOk());
        String projectId = body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"Chief Medical Officer"}
                                """.formatted(clientId)))
                .andReturn()).get("id").asText();

        askAndAwait(admin, projectId, null, "Top hospital groups");

        String system = model.lastPrompt().getSystemMessage().getText();
        assertThat(system)
                .contains("The consultant works for Gulf Search Partners, a search agency")
                .contains("- Name: Harbour Health")
                .contains("- Headquarters: Saudi Arabia")
                .contains("- Sectors: Hospitals")
                .contains("- Competitors: Dallah Health")
                .contains("- Role: Chief Medical Officer")
                .doesNotContain("departments or business units")
                .doesNotContain("{hiring}")
                .doesNotContain("{brief}");
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
        // Shipped flake: the event is written in pieces, and reading on "event:done" alone sometimes caught
        // its data line still empty. The blank line that ends an SSE event is what says it is all there.
        String data = Awaitility.await()
                .atMost(Duration.ofMillis(STREAM_WAIT_MS))
                .pollInterval(Duration.ofMillis(50))
                .until(() -> COMPLETE_DONE_EVENT.matcher(stream.getResponse().getContentAsString()), Matcher::find)
                .group(1);
        return json.readTree(data);
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
