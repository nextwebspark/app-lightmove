package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Who may read and change the pipeline: any member reads it, a client seat included, since every
 * candidate read already carries its labels; only {@code WORKSPACE_MANAGE} changes it, every change is
 * audited as the workspace's {@code pipeline} section, and another workspace's ids are not found.
 */
@IntegrationTest
class CandidatePipelineControllerTest extends PipelineFlowSupport {

    @Test
    @DisplayName("a member and a client seat read the pipeline; neither may change it")
    void onlyAnAdminChangesThePipeline() throws Exception {
        firm("Pipeline Gate Firm");
        String projectId = mandate("Chief Financial Officer");
        String member = member("noor");
        String client = clientOn(projectId);
        JsonNode catalog = catalog();
        String interested = stageIn(catalog, "interested").get("id").asText();
        String researching = statusIn(catalog, "researching").get("id").asText();

        for (String reader : new String[] {member, client}) {
            JsonNode read = body(mvc.perform(get(PIPELINE).header("Authorization", "Bearer " + reader))
                    .andExpect(status().isOk())
                    .andReturn());
            assertThat(read.get("stages")).hasSize(6);
            assertThat(write(reader, "POST", PIPELINE + "/stages", """
                    {"label":"Vetting"}""").getResponse().getStatus()).isEqualTo(403);
            assertThat(write(reader, "POST", PIPELINE + "/statuses", """
                    {"stageId":"%s","label":"Vetting"}""".formatted(interested)).getResponse().getStatus())
                    .isEqualTo(403);
            assertThat(write(reader, "PATCH", PIPELINE + "/statuses/" + researching, """
                    {"label":"Digging"}""").getResponse().getStatus()).isEqualTo(403);
        }
        assertThat(statusIn(catalog(), "researching").get("label").asText()).isEqualTo("Researching");
    }

    @Test
    @DisplayName("every change is audited as the pipeline section of the workspace")
    void everyChangeIsAudited() throws Exception {
        firm("Pipeline Audit Firm");
        String interested = stageIn(catalog(), "interested").get("id").asText();

        write(admin, "POST", PIPELINE + "/statuses", """
                {"stageId":"%s","label":"Counter-offered"}""".formatted(interested));
        write(admin, "POST", PIPELINE + "/stages", """
                {"label":"Vetting"}""");

        assertThat(db.queryForList("""
                select metadata ->> 'change' from app_lm_audit_event
                where event_type = 'WORKSPACE_UPDATED' and workspace_id = ?::uuid
                  and metadata ->> 'section' = 'pipeline'
                order by occurred_at""", String.class, workspaceId))
                .containsExactly("statusCreated", "stageCreated");
    }

    @Test
    @DisplayName("another workspace's stage or status is not found, and its catalog is never read")
    void anotherWorkspacesPipelineIsNotFound() throws Exception {
        firm("Pipeline Owner Firm");
        JsonNode theirs = catalog();
        String theirStage = stageIn(theirs, "interested").get("id").asText();
        String theirStatus = statusIn(theirs, "researching").get("id").asText();

        domain = "pipeline-other-" + domain;
        firm("Pipeline Other Firm");
        JsonNode ours = catalog();

        assertThat(write(admin, "PATCH", PIPELINE + "/stages/" + theirStage, """
                {"label":"Taken over"}""").getResponse().getStatus()).isEqualTo(404);
        assertThat(write(admin, "PATCH", PIPELINE + "/statuses/" + theirStatus, """
                {"label":"Taken over"}""").getResponse().getStatus()).isEqualTo(404);
        assertThat(write(admin, "POST", PIPELINE + "/statuses", """
                {"stageId":"%s","label":"Planted"}""".formatted(theirStage)).getResponse().getStatus())
                .isEqualTo(404);
        assertThat(statusIn(ours, "researching").get("id").asText()).isNotEqualTo(theirStatus);
    }
}
