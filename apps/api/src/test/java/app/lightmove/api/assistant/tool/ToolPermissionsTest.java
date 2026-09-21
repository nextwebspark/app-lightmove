package app.lightmove.api.assistant.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

/**
 * That an unguarded tool cannot start the application.
 *
 * <p>This is what makes the guard enforcement rather than a convention: every case below is a
 * mistake a reviewer could miss, and each one fails at wiring instead of at the first conversation
 * that reaches for it.
 */
class ToolPermissionsTest {

    @Test
    @DisplayName("reads the declared action of each tool, keyed on the name the model will call")
    void readsDeclaredActions() {
        ToolPermissions permissions = new ToolPermissions(List.of(new WellDeclaredTools()));

        assertThat(permissions.toolNames()).containsExactlyInAnyOrder("marketTool", "renamedTool");
        assertThat(permissions.requiredBy("marketTool"))
                .isEqualTo(new ToolPermission.WorkspaceActionRequired(WorkspaceAction.PROJECT_BROWSE));
        assertThat(permissions.requiredBy("renamedTool"))
                .isEqualTo(new ToolPermission.ProjectActionRequired(ProjectAction.WORK_VIEW, "projectId"));
    }

    @Test
    @DisplayName("refuses to start when a tool declares no permission at all")
    void refusesAnUndeclaredTool() {
        assertThatThrownBy(() -> new ToolPermissions(List.of(new UndeclaredTools())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UndeclaredTools#unguarded")
                .hasMessageContaining("declares no @RequiresWorkspaceAction or @RequiresProjectAction");
    }

    @Test
    @DisplayName("refuses a tool guarded at both tiers, which would authorise at whichever ran first")
    void refusesBothTiers() {
        assertThatThrownBy(() -> new ToolPermissions(List.of(new DoublyDeclaredTools())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("a tool is guarded at one tier");
    }

    @Test
    @DisplayName("refuses a project tool naming an argument it does not have")
    void refusesAMissingProjectArgument() {
        assertThatThrownBy(() -> new ToolPermissions(List.of(new MisnamedArgumentTools())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("names the project id argument 'mandateId', which it does not declare");
    }

    @Test
    @DisplayName("a tool it never saw is refused, not passed")
    void refusesAnUnknownTool() {
        ToolPermissions permissions = new ToolPermissions(List.of(new WellDeclaredTools()));

        assertThatThrownBy(() -> permissions.requiredBy("somethingElse"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("declares no permission");
    }

    static class WellDeclaredTools implements AssistantToolSubject {

        @Tool(description = "market")
        @RequiresWorkspaceAction(WorkspaceAction.PROJECT_BROWSE)
        public String marketTool(String query) {
            return query;
        }

        @Tool(name = "renamedTool", description = "mandate")
        @RequiresProjectAction(ProjectAction.WORK_VIEW)
        public String mandateTool(String projectId) {
            return projectId;
        }
    }

    static class UndeclaredTools implements AssistantToolSubject {

        @Tool(description = "forgot the guard")
        public String unguarded(String anything) {
            return anything;
        }
    }

    static class DoublyDeclaredTools implements AssistantToolSubject {

        @Tool(description = "both")
        @RequiresWorkspaceAction(WorkspaceAction.PROJECT_BROWSE)
        @RequiresProjectAction(ProjectAction.WORK_VIEW)
        public String confused(String projectId) {
            return projectId;
        }
    }

    static class MisnamedArgumentTools implements AssistantToolSubject {

        @Tool(description = "typo")
        @RequiresProjectAction(value = ProjectAction.WORK_VIEW, projectId = "mandateId")
        public String misnamed(String projectId) {
            return projectId;
        }
    }
}
