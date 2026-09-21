package app.lightmove.api.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.ApolloUniverse;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.assistant.constant.AssistantEventKind;
import app.lightmove.api.assistant.model.AssistantEvent;
import app.lightmove.api.assistant.model.AssistantProposal;
import app.lightmove.api.assistant.model.ProposalOrigin;
import app.lightmove.api.assistant.model.ProposedCompany;
import app.lightmove.api.assistant.repository.AssistantEventRepository;
import tools.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Accepting a proposal against real membership rows, which is the only place the gate can be proved.
 *
 * <p>The proposal is written straight into the event log rather than produced by a turn:
 * {@code RecordingAssistantTurnRunner} stands in for the model across the assistant's suites, so no
 * turn started here would ever reach a tool. What is under test is the accept — its gate, its
 * routing and its counts.
 */
@IntegrationTest
class AssistantProposalIntegrationTest extends FlowTestSupport {

    @Autowired
    private JdbcTemplate db;

    @Autowired
    private AssistantEventRepository events;

    @Autowired
    private ObjectMapper json;

    private ApolloUniverse universe;

    @BeforeEach
    void freshUniverse() {
        universe = new ApolloUniverse(db);
        universe.reset();
    }

    @Test
    @DisplayName("a lead files the rows they ticked, badged as the assistant's door")
    void filesTheTickedRows() throws Exception {
        String admin = adminOf("Proposal Firm");
        String projectId = project(admin);
        universe.company("a1", "ACWA Power").industry("oil & energy").employees(4_000).insert();
        universe.company("a2", "Marafiq").industry("oil & energy").employees(2_400).insert();
        UUID turnId = turnWithProposal(admin, projectId, "a1", "a2");

        mvc.perform(accept(admin, turnId, """
                        {"refs":["c1"],"status":"shortlisted"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(1))
                .andExpect(jsonPath("$.skipped").value(0));

        assertThat(db.queryForList(
                "SELECT company_name, source, status FROM app_lm_project_triage_company"
                        + " WHERE project_id = ?", UUID.fromString(projectId)))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.get("company_name")).isEqualTo("ACWA Power");
                    assertThat(row.get("source")).isEqualTo("ASSISTANT");
                    assertThat(row.get("status")).isEqualTo("SHORTLISTED");
                });
    }

    @Test
    @DisplayName("a client representative on the mandate is refused — WORK_VIEW is not WORK_EXECUTE")
    void refusesAClientRepresentative() throws Exception {
        String admin = adminOf("Proposal Client Firm");
        String clientId = client(admin);
        String projectId = project(admin, clientId);
        universe.company("a1", "ACWA Power").industry("oil & energy").employees(4_000).insert();

        String repEmail = "clara@client-" + domain;
        String representativeId = inviteRepresentative(admin, clientId, repEmail);
        acceptAsNewUser(email.latestTokenFor(repEmail), "Clara Client");
        attachRepresentative(admin, projectId, representativeId);
        String rep = login(repEmail);
        UUID turnId = turnWithProposal(rep, projectId, "a1");

        // The seat that reads a mandate is not the seat that works it, which is the whole reason
        // the proposing tool declares WORK_EXECUTE too: a card whose buttons all refuse is worse
        // than no card.
        mvc.perform(accept(rep, turnId, """
                        {"refs":["c1"]}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("another person's turn is a 404, and tells them nothing else")
    void hidesAnotherPersonsTurn() throws Exception {
        String admin = adminOf("Proposal Privacy Firm");
        String projectId = project(admin);
        universe.company("a1", "ACWA Power").industry("oil & energy").employees(4_000).insert();
        UUID turnId = turnWithProposal(admin, projectId, "a1");
        inviteAndAccept(admin, "Rob Researcher", "rob@" + domain, "MEMBER");

        mvc.perform(accept(login("rob@" + domain), turnId, """
                        {"refs":["c1"]}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a second accept is refused rather than filing nothing and saying so")
    void refusesASecondAccept() throws Exception {
        String admin = adminOf("Proposal Twice Firm");
        String projectId = project(admin);
        universe.company("a1", "ACWA Power").industry("oil & energy").employees(4_000).insert();
        UUID turnId = turnWithProposal(admin, projectId, "a1");

        mvc.perform(accept(admin, turnId, """
                        {"refs":["c1"]}""")).andExpect(status().isOk());
        mvc.perform(accept(admin, turnId, """
                        {"refs":["c1"]}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSISTANT_PROPOSAL_ALREADY_ACCEPTED"));
    }

    @Test
    @DisplayName("a company ruled off limits between proposal and accept is skipped, not filed")
    void dropsOneThatWentOffLimits() throws Exception {
        String admin = adminOf("Proposal Off Limits Firm");
        String projectId = project(admin);
        universe.company("a1", "ACWA Power").industry("oil & energy").employees(4_000).insert();
        UUID turnId = turnWithProposal(admin, projectId, "a1");

        mvc.perform(put("/api/v1/projects/" + projectId + "/strategy/off-limits")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"apolloAccountIds":["a1"]}"""))
                .andExpect(status().isOk());

        mvc.perform(accept(admin, turnId, """
                        {"refs":["c1"]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(0))
                .andExpect(jsonPath("$.skipped").value(1));
    }

    @Test
    @DisplayName("a refetched thread carries the proposal, and its outcome once filed")
    void carriesTheProposalOnAThreadRead() throws Exception {
        String admin = adminOf("Proposal Refresh Firm");
        String projectId = project(admin);
        universe.company("a1", "ACWA Power").industry("oil & energy").employees(4_000).insert();
        UUID turnId = turnWithProposal(admin, projectId, "a1");
        UUID threadId = threadOf(turnId);

        mvc.perform(get("/api/v1/assistant/threads/" + threadId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.turns[0].proposal.title").value("Six IPPs"))
                .andExpect(jsonPath("$.turns[0].proposal.companies[0].companyName").value("ACWA Power"))
                .andExpect(jsonPath("$.turns[0].proposal.accepted").doesNotExist());

        mvc.perform(accept(admin, turnId, """
                        {"refs":["c1"],"status":"shortlisted"}""")).andExpect(status().isOk());

        // The proposal event is immutable, so the outcome is a second event read alongside it —
        // which is what stops a reloaded panel offering a card somebody already actioned.
        mvc.perform(get("/api/v1/assistant/threads/" + threadId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.turns[0].proposal.accepted.added").value(1))
                .andExpect(jsonPath("$.turns[0].proposal.accepted.status").value("shortlisted"));
    }

    private MockHttpServletRequestBuilder accept(String token, UUID turnId, String body) {
        return post("/api/v1/assistant/turns/" + turnId + "/proposal/accept")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    /**
     * A turn that asked something, with a proposal written into its log as a tool would have.
     *
     * <p>Waits for the turn to settle first, and that is not tidiness: the appender allocates
     * {@code max(seq) + 1} on the assumption a turn has one writer, so appending while the worker is
     * still emitting would race it onto V65's unique index — a flake that would only ever appear in
     * CI.
     */
    private UUID turnWithProposal(String token, String projectId, String... apolloAccountIds)
            throws Exception {
        JsonNode accepted = body(mvc.perform(post("/api/v1/assistant/ask")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"Top IPPs in Saudi Arabia","projectId":"%s"}"""
                                .formatted(projectId)))
                .andExpect(status().isAccepted())
                .andReturn());
        String turnId = accepted.get("id").asText();
        awaitTurnSettled(token, accepted.get("threadId").asText(), turnId);

        List<ProposedCompany> companies = new ArrayList<>();
        for (int index = 0; index < apolloAccountIds.length; index++) {
            companies.add(new ProposedCompany("c" + (index + 1), ProposalOrigin.UNIVERSE,
                    apolloAccountIds[index], "Proposed " + apolloAccountIds[index], "Saudi Arabia",
                    400));
        }
        AssistantProposal proposal = new AssistantProposal(UUID.fromString(projectId), "Six IPPs",
                companies);
        UUID turn = UUID.fromString(turnId);
        events.save(AssistantEvent.of(turn, events.maxSeq(turn) + 1, AssistantEventKind.PROPOSAL,
                json.convertValue(proposal, new TypeReference<Map<String, Object>>() {
                })));
        return turn;
    }

    private UUID threadOf(UUID turnId) {
        return db.queryForObject("SELECT thread_id FROM app_lm_assistant_turn WHERE id = ?",
                UUID.class, turnId);
    }

    private String adminOf(String workspaceName) throws Exception {
        createWorkspace(verifiedUser("Ada Admin", "ada@" + domain), workspaceName);
        return login("ada@" + domain);
    }

    private String client(String token) throws Exception {
        return body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Acme Corp"}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private String project(String token) throws Exception {
        return project(token, client(token));
    }

    private String project(String token, String clientId) throws Exception {
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"CFO Search"}""".formatted(clientId)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private String inviteRepresentative(String token, String clientId, String repEmail)
            throws Exception {
        return body(mvc.perform(post("/api/v1/clients/" + clientId + "/representatives")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Clara Client","position":"Chair","email":"%s"}"""
                                .formatted(repEmail)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private void attachRepresentative(String token, String projectId, String representativeId)
            throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectId + "/representatives")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"representativeId":"%s"}""".formatted(representativeId)))
                .andExpect(status().isOk());
    }

    private String acceptAsNewUser(String token, String fullName) throws Exception {
        return body(mvc.perform(post("/api/v1/onboarding/accept-invitation-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","fullName":"%s","password":"%s"}"""
                                .formatted(token, fullName, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn()).get("accessToken").asText();
    }
}
