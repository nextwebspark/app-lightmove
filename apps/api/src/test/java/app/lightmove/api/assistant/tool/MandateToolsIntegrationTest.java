package app.lightmove.api.assistant.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/** What the model is told about the position, read from real rows and never written back. */
@IntegrationTest
class MandateToolsIntegrationTest extends FlowTestSupport {

    @Autowired
    private MandateTools tools;

    @Autowired
    private JdbcTemplate db;

    @Test
    @DisplayName("the brief names the position and nothing about the client, and leaves compensation out")
    void readsThePosition() throws Exception {
        Mandate mandate = mandate("Brief Reading Firm");
        TurnRecorder recorder = new TurnRecorder(step -> { });

        MandateBrief brief = tools.readMandateBrief(context(mandate, recorder));

        assertThat(brief.roleTitle()).isEqualTo("Chief Financial Officer");
        assertThat(MandateBrief.class.getRecordComponents())
                .extracting("name")
                .doesNotContain("compensation", "internalContext", "clientName", "clientSector");
        assertThat(recorder.steps()).singleElement().satisfies(step -> {
            assertThat(step.label()).isEqualTo("Reading the position brief");
            assertThat(step.detail()).startsWith("Chief Financial Officer");
        });
    }

    @Test
    @DisplayName("a mandate with no brief reads blank and none is drafted")
    void neverDraftsABrief() throws Exception {
        Mandate mandate = mandate("Brief Absent Firm");
        db.update("DELETE FROM app_lm_position WHERE project_id = ?", mandate.projectId);

        MandateBrief brief = tools.readMandateBrief(context(mandate, new TurnRecorder(step -> { })));

        assertThat(brief.responsibilities()).isEmpty();
        assertThat(db.queryForObject("SELECT count(*) FROM app_lm_position WHERE project_id = ?",
                Integer.class, mandate.projectId)).isZero();
    }

    private record Mandate(UUID workspaceId, UUID projectId) {}

    private Mandate mandate(String firmName) throws Exception {
        String email = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", email), firmName);
        String admin = login(email);
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Group Finance"}"""))
                .andReturn()).get("id").asText();
        UUID projectId = UUID.fromString(body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"Chief Financial Officer"}
                                """.formatted(clientId)))
                .andReturn()).get("id").asText());
        UUID workspaceId = db.queryForObject("SELECT workspace_id FROM app_lm_project WHERE id = ?",
                UUID.class, projectId);
        return new Mandate(workspaceId, projectId);
    }

    private static ToolContext context(Mandate mandate, TurnRecorder recorder) {
        return new ToolContext(
                new AssistantToolContext(mandate.workspaceId, mandate.projectId, recorder).asMap());
    }
}
