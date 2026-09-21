package app.lightmove.api.assistant.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import app.lightmove.api.assistant.service.AssistantEventSink;
import app.lightmove.api.core.audit.constant.SecurityEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.rbac.ProjectAccess;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.databind.ObjectMapper;

/**
 * The guard itself: what it lets through, what it refuses, and what it tells the model about why.
 *
 * <p>The refusal cases matter more than the pass. A model that learns a mandate exists from the
 * shape of a denial has been handed an enumeration oracle in prose, and a denial that reaches the
 * conversation as an exception message hands it the reason in words.
 */
class AuthorisingToolCallbackTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final UUID TURN = UUID.randomUUID();
    private static final UUID PROJECT = UUID.randomUUID();
    private static final AssistantToolCaller CALLER = new AssistantToolCaller(USER, WORKSPACE, TURN);

    private final WorkspaceAccess workspaceAccess = mock(WorkspaceAccess.class);
    private final ProjectAccess projectAccess = mock(ProjectAccess.class);
    private final AuditService audit = mock(AuditService.class, RETURNS_DEEP_STUBS);
    private final ToolPermissions permissions = new ToolPermissions(List.of(new GuardedTools()));
    private final ToolAuthoriser authoriser =
            new ToolAuthoriser(workspaceAccess, projectAccess, new ObjectMapper());
    private final RecordingSink sink = new RecordingSink();

    @Test
    @DisplayName("a call with no tool context is refused outright rather than guessed at")
    void refusesACallWithNoContext() {
        assertThatThrownBy(() -> guarded("marketTool").call("{}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no tool context");
    }

    @Test
    @DisplayName("a call carrying another turn's caller is refused")
    void refusesAnotherTurnsCaller() {
        AssistantToolCaller elsewhere = new AssistantToolCaller(USER, WORKSPACE, UUID.randomUUID());

        assertThatThrownBy(() -> guarded("marketTool").call("{}", contextOf(elsewhere)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("a different turn");
    }

    @Test
    @DisplayName("an authorised call reaches the tool, and both halves reach the trace")
    void delegatesAnAuthorisedCall() {
        RecordingToolCallback delegate = new RecordingToolCallback("marketTool", "six companies");

        String answer = guarded(delegate).call("{\"query\":\"utilities\"}", contextOf(CALLER));

        assertThat(answer).isEqualTo("six companies");
        assertThat(delegate.inputs).containsExactly("{\"query\":\"utilities\"}");
        verify(workspaceAccess).requireAction(USER, WORKSPACE, WorkspaceAction.PROJECT_BROWSE);
        assertThat(sink.calls).containsExactly("marketTool:{\"query\":\"utilities\"}");
        assertThat(sink.results).containsExactly("marketTool:six companies");
    }

    @Test
    @DisplayName("the project tool is authorised against the id in the arguments, not the turn")
    void authorisesAgainstTheArguments() {
        RecordingToolCallback delegate = new RecordingToolCallback("mandateTool", "two companies");

        guarded(delegate).call("{\"projectId\":\"" + PROJECT + "\"}", contextOf(CALLER));

        verify(projectAccess).requireAction(USER, WORKSPACE, PROJECT, ProjectAction.WORK_VIEW);
    }

    @Test
    @DisplayName("a refused call answers the model, never the tool, and is recorded")
    void refusesWithoutDelegating() {
        RecordingToolCallback delegate = new RecordingToolCallback("marketTool", "six companies");
        doThrow(new ApiException(ErrorCode.FORBIDDEN, "Requires the PROJECT_BROWSE action"))
                .when(workspaceAccess).requireAction(any(), any(), any());

        String answer = guarded(delegate).call("{}", contextOf(CALLER));

        assertThat(answer).isEqualTo(AuthorisingToolCallback.REFUSED);
        assertThat(delegate.inputs).isEmpty();
        assertThat(sink.results).containsExactly("marketTool:" + AuthorisingToolCallback.REFUSED);
        verify(audit).event(SecurityEventType.ASSISTANT_TOOL_DENIED);
    }

    @Test
    @DisplayName("a mandate that does not exist and one the caller is not on read identically")
    void tellsTheModelNothingAboutWhy() {
        String absent = refusalFor(new ApiException(ErrorCode.NOT_FOUND, "no such project"));
        String noSeat = refusalFor(new ApiException(ErrorCode.FORBIDDEN, "Not on this project's team"));

        assertThat(absent)
                .as("the two answers are the whole enumeration oracle; they must not differ at all")
                .isEqualTo(noSeat)
                .doesNotContain("project");
    }

    @Test
    @DisplayName("a tool call naming no mandate is refused rather than authorised against null")
    void refusesAnUnnamedProject() {
        RecordingToolCallback delegate = new RecordingToolCallback("mandateTool", "two companies");

        String answer = guarded(delegate).call("{}", contextOf(CALLER));

        assertThat(answer).isEqualTo(AuthorisingToolCallback.REFUSED);
        assertThat(delegate.inputs).isEmpty();
        verify(projectAccess, never()).requireAction(any(), any(), any(), any());
    }

    @Test
    @DisplayName("a failure inside the tool never reaches the model as its own message")
    void keepsABodyFailureOffTheConversation() {
        ToolCallback exploding = exploding("marketTool",
                new ApiException(ErrorCode.NOT_FOUND, "no project 6f21 in workspace 9c04"));

        String answer = guarded(exploding).call("{}", contextOf(CALLER));

        // Letting it propagate reads as the safer choice and is not: MethodToolCallback wraps it,
        // throw-exception-on-error defaults to false, and the processor hands the cause's message
        // back as the tool result. ApiException's detail is licensed to quote the request because it
        // never leaves the server, which inside a tool body stopped being true.
        assertThat(answer)
                .isEqualTo(AuthorisingToolCallback.FAILED)
                .doesNotContain("6f21")
                .doesNotContain("9c04");
    }

    @Test
    @DisplayName("a failure and a refusal are different answers, and neither says why")
    void tellsAFailureFromARefusal() {
        doThrow(new ApiException(ErrorCode.FORBIDDEN, "Requires the PROJECT_BROWSE action"))
                .when(workspaceAccess).requireAction(any(), any(), any());
        String refusal = guarded("marketTool").call("{}", contextOf(CALLER));

        assertThat(refusal).isEqualTo(AuthorisingToolCallback.REFUSED)
                .isNotEqualTo(AuthorisingToolCallback.FAILED);
    }

    @Test
    @DisplayName("a tool that blows up is traced as a result, not left with a call and no answer")
    void tracesAFailureAsAResult() {
        ToolCallback exploding = exploding("marketTool", new IllegalStateException("the database is down"));

        guarded(exploding).call("{}", contextOf(CALLER));

        assertThat(sink.calls).containsExactly("marketTool:{}");
        assertThat(sink.results).containsExactly("marketTool:" + AuthorisingToolCallback.FAILED);
    }

    private static ToolCallback exploding(String toolName, RuntimeException failure) {
        return new RecordingToolCallback(toolName, null) {
            @Override
            public String call(String toolInput, ToolContext toolContext) {
                throw failure;
            }
        };
    }

    private String refusalFor(ApiException denial) {
        ProjectAccess denying = mock(ProjectAccess.class);
        doThrow(denial).when(denying).requireAction(any(), any(), any(), eq(ProjectAction.WORK_VIEW));
        ToolAuthoriser strict = new ToolAuthoriser(workspaceAccess, denying, new ObjectMapper());
        return new AuthorisingToolCallback(new RecordingToolCallback("mandateTool", "rows"),
                permissions, strict, CALLER, audit, sink, "corr-1")
                .call("{\"projectId\":\"" + PROJECT + "\"}", contextOf(CALLER));
    }

    private AuthorisingToolCallback guarded(String toolName) {
        return guarded(new RecordingToolCallback(toolName, "answer"));
    }

    private AuthorisingToolCallback guarded(ToolCallback delegate) {
        return new AuthorisingToolCallback(delegate, permissions, authoriser, CALLER, audit, sink,
                "corr-1");
    }

    private static ToolContext contextOf(AssistantToolCaller caller) {
        return new ToolContext(Map.copyOf(ToolCallerContext.of(caller, text -> {
        })));
    }

    /** The two tiers, so the guard has something real to look up. Bodies are never reached here. */
    static class GuardedTools implements AssistantToolSubject {

        @Tool(description = "market")
        @RequiresWorkspaceAction(WorkspaceAction.PROJECT_BROWSE)
        public String marketTool(String query) {
            return query;
        }

        @Tool(description = "mandate")
        @RequiresProjectAction(ProjectAction.WORK_VIEW)
        public String mandateTool(String projectId) {
            return projectId;
        }
    }

    static class RecordingToolCallback implements ToolCallback {

        private final String name;
        private final String answer;
        final List<String> inputs = new ArrayList<>();

        RecordingToolCallback(String name, String answer) {
            this.name = name;
            this.answer = answer;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return ToolDefinition.builder().name(name).description(name).inputSchema("{}").build();
        }

        @Override
        public String call(String toolInput) {
            return call(toolInput, null);
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            inputs.add(toolInput);
            return answer;
        }
    }

    static class RecordingSink implements AssistantEventSink {

        final List<String> deltas = new ArrayList<>();
        final List<String> calls = new ArrayList<>();
        final List<String> results = new ArrayList<>();

        @Override
        public void delta(String text) {
            deltas.add(text);
        }

        @Override
        public void toolCalled(String toolName, String arguments) {
            calls.add(toolName + ":" + arguments);
        }

        @Override
        public void toolResult(String toolName, String result) {
            results.add(toolName + ":" + result);
        }
    }
}
