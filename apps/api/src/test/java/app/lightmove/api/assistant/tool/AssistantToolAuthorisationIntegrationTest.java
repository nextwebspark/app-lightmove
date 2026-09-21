package app.lightmove.api.assistant.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.assistant.service.AssistantEventSink;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The guard against real membership rows, which is the only place it can actually be proved.
 *
 * <p>Drives the callbacks directly rather than through a turn. {@code RecordingAssistantTurnRunner}
 * stands in for the model across the assistant's suites, so a turn started here would never reach a
 * tool — and what is under test is the guard and the rbac tables beneath it, not the model.
 */
@IntegrationTest
class AssistantToolAuthorisationIntegrationTest extends FlowTestSupport {

    private static final String MARKET_TOOL = "searchCompanyUniverse";
    private static final String MANDATE_TOOL = "listMandateCompanies";

    @Autowired
    private AssistantToolset toolset;

    @Autowired
    private JdbcTemplate db;

    @Test
    @DisplayName("a staff lead reaches both tiers for their own mandate")
    void staffReachBothTiers() throws Exception {
        String admin = staffToken();
        String project = createProject(admin, createCustomClient(admin, "Acme Corp"), "CFO Search");

        AssistantToolCaller caller = callerFor("ada@" + domain);

        assertThat(call(caller, MARKET_TOOL, "{\"companyName\":\"Acme\"}"))
                .isNotEqualTo(AuthorisingToolCallback.REFUSED);
        assertThat(call(caller, MANDATE_TOOL, mandateArguments(project)))
                .isNotEqualTo(AuthorisingToolCallback.REFUSED);
    }

    @Test
    @DisplayName("a pure client is refused the market but keeps the mandate they are attached to")
    void aPureClientIsHeldToTheirMandate() throws Exception {
        String admin = staffToken();
        String clientId = createCustomClient(admin, "Acme Corp");
        String project = createProject(admin, clientId, "CFO Search");

        String repEmail = "clara@client-" + domain;
        String representativeId = inviteRepresentative(admin, clientId, "Clara Client", "Chair", repEmail);
        acceptAsNewUser(email.latestTokenFor(repEmail), "Clara Client");
        attachRepresentative(admin, project, representativeId);

        AssistantToolCaller client = callerFor(repEmail);

        assertThat(call(client, MARKET_TOOL, "{\"companyName\":\"Acme\"}"))
                .as("PROJECT_BROWSE is ADMIN and MEMBER only, so the market side is shut to a client")
                .isEqualTo(AuthorisingToolCallback.REFUSED);
        assertThat(call(client, MANDATE_TOOL, mandateArguments(project)))
                .as("WORK_VIEW is the client seat's own grant, and it is what reads the mandate")
                .isNotEqualTo(AuthorisingToolCallback.REFUSED);
    }

    @Test
    @DisplayName("a member with no seat is refused, and cannot tell that mandate from a fictional one")
    void tellsAMemberNothingAboutAMandateTheyAreNotOn() throws Exception {
        String admin = staffToken();
        String project = createProject(admin, createCustomClient(admin, "Acme Corp"), "CFO Search");
        inviteAndAccept(admin, "Rob Researcher", "rob@" + domain, "MEMBER");

        AssistantToolCaller unseated = callerFor("rob@" + domain);

        String realMandate = call(unseated, MANDATE_TOOL, mandateArguments(project));
        String fiction = call(unseated, MANDATE_TOOL, mandateArguments(UUID.randomUUID().toString()));

        assertThat(realMandate).isEqualTo(AuthorisingToolCallback.REFUSED);
        assertThat(fiction)
                .as("a real mandate answers 403 and an absent one 404; the model must see neither")
                .isEqualTo(realMandate);
    }

    @Test
    @DisplayName("a mandate in another workspace is refused like one that does not exist")
    void refusesAnotherWorkspacesMandate() throws Exception {
        String admin = staffToken();
        String project = createProject(admin, createCustomClient(admin, "Acme Corp"), "CFO Search");

        String outsider = verifiedUser("Otto Outsider", "otto@other-" + domain);
        createWorkspace(outsider, "Other Firm");
        AssistantToolCaller elsewhere = callerFor("otto@other-" + domain);

        assertThat(call(elsewhere, MANDATE_TOOL, mandateArguments(project)))
                .isEqualTo(AuthorisingToolCallback.REFUSED);
    }

    @Test
    @DisplayName("a tool call naming no mandate at all is refused rather than defaulted")
    void refusesAnUnnamedMandate() throws Exception {
        String admin = staffToken();
        createProject(admin, createCustomClient(admin, "Acme Corp"), "CFO Search");

        assertThat(call(callerFor("ada@" + domain), MANDATE_TOOL, "{\"stage\":\"inUniverse\"}"))
                .isEqualTo(AuthorisingToolCallback.REFUSED);
    }

    private String call(AssistantToolCaller caller, String toolName, String arguments) {
        ToolCallback[] tools = toolset.forTurn(caller, noTrace());
        ToolCallback tool = Arrays.stream(tools)
                .filter(candidate -> candidate.getToolDefinition().name().equals(toolName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no tool named " + toolName));
        return tool.call(arguments, new ToolContext(ToolCallerContext.of(caller)));
    }

    private static String mandateArguments(String projectId) {
        return "{\"projectId\":\"%s\",\"stage\":\"inUniverse\"}".formatted(projectId);
    }

    /** The identity the worker rebuilds from a turn row, taken here from the rows it reads. */
    private AssistantToolCaller callerFor(String emailAddress) {
        UUID userId = db.queryForObject(
                "SELECT id FROM app_lm_user WHERE lower(email) = lower(?)", UUID.class, emailAddress);
        UUID workspaceId = db.queryForObject(
                "SELECT workspace_id FROM app_lm_workspace_member WHERE user_id = ? AND status = 'ACTIVE'",
                UUID.class, userId);
        return new AssistantToolCaller(userId, workspaceId, UUID.randomUUID());
    }

    private static AssistantEventSink noTrace() {
        return text -> {
        };
    }

    private String staffToken() throws Exception {
        String token = verifiedUser("Ada Admin", "ada@" + domain);
        createWorkspace(token, "Assistant Firm");
        return login("ada@" + domain);
    }

    private String createCustomClient(String adminToken, String name) throws Exception {
        return body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customName\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private String createProject(String adminToken, String clientId, String positionTitle)
            throws Exception {
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
}
