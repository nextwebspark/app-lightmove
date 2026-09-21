package app.lightmove.api.assistant.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.assistant.service.AssistantEventSink;
import java.util.Arrays;
import java.util.List;
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

    /**
     * Read off the toolset rather than listed, so a tool added at the mandate tier joins every case
     * below without anyone remembering to put it here.
     */
    private List<String> mandateTools() {
        List<String> tools = toolset.permissions().toolNames().stream()
                .filter(name -> toolset.permissions().requiredBy(name)
                        instanceof ToolPermission.ProjectActionRequired)
                .sorted()
                .toList();
        assertThat(tools).as("an empty mandate tier would pass every case below vacuously").isNotEmpty();
        return tools;
    }

    @Test
    @DisplayName("a staff lead reaches both tiers for their own mandate")
    void staffReachBothTiers() throws Exception {
        String admin = staffToken();
        String project = createProject(admin, createCustomClient(admin, "Acme Corp"), "CFO Search");

        AssistantToolCaller caller = callerFor("ada@" + domain);

        assertThat(call(caller, MARKET_TOOL, "{\"companyName\":\"Acme\"}"))
                .isNotEqualTo(AuthorisingToolCallback.REFUSED);
        for (String tool : mandateTools()) {
            assertThat(call(caller, tool, mandateArguments(tool, project)))
                    .as(tool + " answers a lead on their own mandate")
                    .isNotEqualTo(AuthorisingToolCallback.REFUSED);
        }
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
        for (String tool : mandateTools()) {
            assertThat(call(client, tool, mandateArguments(tool, project)))
                    .as("WORK_VIEW is the client seat's own grant, and it is what " + tool + " reads")
                    .isNotEqualTo(AuthorisingToolCallback.REFUSED);
        }
    }

    @Test
    @DisplayName("a member with no seat is refused, and cannot tell that mandate from a fictional one")
    void tellsAMemberNothingAboutAMandateTheyAreNotOn() throws Exception {
        String admin = staffToken();
        String project = createProject(admin, createCustomClient(admin, "Acme Corp"), "CFO Search");
        inviteAndAccept(admin, "Rob Researcher", "rob@" + domain, "MEMBER");

        AssistantToolCaller unseated = callerFor("rob@" + domain);

        for (String tool : mandateTools()) {
            String realMandate = call(unseated, tool, mandateArguments(tool, project));
            String fiction = call(unseated, tool,
                    mandateArguments(tool, UUID.randomUUID().toString()));

            assertThat(realMandate).as(tool + " is shut to a member with no seat")
                    .isEqualTo(AuthorisingToolCallback.REFUSED);
            assertThat(fiction)
                    .as("a real mandate answers 403 and an absent one 404; the model must see neither")
                    .isEqualTo(realMandate);
        }
    }

    @Test
    @DisplayName("a mandate in another workspace is refused like one that does not exist")
    void refusesAnotherWorkspacesMandate() throws Exception {
        String admin = staffToken();
        String project = createProject(admin, createCustomClient(admin, "Acme Corp"), "CFO Search");

        String outsider = verifiedUser("Otto Outsider", "otto@other-" + domain);
        createWorkspace(outsider, "Other Firm");
        AssistantToolCaller elsewhere = callerFor("otto@other-" + domain);

        for (String tool : mandateTools()) {
            assertThat(call(elsewhere, tool, mandateArguments(tool, project)))
                    .as(tool + " is shut to another workspace's member")
                    .isEqualTo(AuthorisingToolCallback.REFUSED);
        }
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
        return tool.call(arguments, new ToolContext(ToolCallerContext.of(caller, noTrace())));
    }

    /** The one mandate tool taking a second required argument; every other is named by its id alone. */
    private static String mandateArguments(String toolName, String projectId) {
        return MANDATE_TOOL.equals(toolName)
                ? "{\"projectId\":\"%s\",\"stage\":\"inUniverse\"}".formatted(projectId)
                : "{\"projectId\":\"%s\"}".formatted(projectId);
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
