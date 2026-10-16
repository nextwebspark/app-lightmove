package app.uncava.api.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.uncava.api.IntegrationTest;
import app.uncava.api.candidate.PipelineFlowSupport;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * Archiving a position: only its lead may, by typing its title back; it leaves every list that does not ask for it
 * and comes back on restore. The workspace admin, who holds every other project action without a seat, does not
 * hold this one.
 */
@IntegrationTest
class ProjectArchiveIntegrationTest extends PipelineFlowSupport {

    private static final String TITLE = "Group CFO – Energy Division";

    private String sara;
    private String projectId;

    @BeforeEach
    void aPositionSaraLeads() throws Exception {
        firm("Archive Firm");
        sara = member("sara");
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Energy Division"}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        projectId = body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + sara)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"%s"}""".formatted(clientId, TITLE)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    @Test
    @DisplayName("the lead archives by typing the title; it leaves the list unless asked for, and restore returns it")
    void theLeadArchivesAndRestores() throws Exception {
        MvcResult mistyped = archive(sara, "Group CFO");
        assertThat(mistyped.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(mistyped)).isEqualTo("ARCHIVE_NAME_MISMATCH");
        assertThat(ids(list(sara, ""))).contains(projectId);

        JsonNode archived = body(archive(sara, "  group cfo - energy division "));
        assertThat(archived.get("archivedAt").isNull()).isFalse();
        assertThat(archived.get("archivedByName").asText()).isEqualTo("sara");

        assertThat(ids(list(sara, ""))).doesNotContain(projectId);
        assertThat(ids(list(sara, "?includeArchived=true"))).contains(projectId);
        assertThat(activityTypes()).contains("PROJECT_ARCHIVED");

        JsonNode restored = body(mvc.perform(post("/api/v1/projects/" + projectId + "/restore")
                        .header("Authorization", "Bearer " + sara))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(restored.get("archivedAt").isNull()).isTrue();
        assertThat(ids(list(sara, ""))).contains(projectId);
        assertThat(activityTypes()).contains("PROJECT_RESTORED");
    }

    @Test
    @DisplayName("a researcher, an admin with no seat and another workspace are refused; an admin seated as lead may")
    void onlyTheLeadArchives() throws Exception {
        String omar = researcherOn(projectId, "omar");
        assertThat(archive(omar, TITLE).getResponse().getStatus()).isEqualTo(403);

        assertThat(archive(admin, TITLE).getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(get("/api/v1/projects/" + projectId + "/archive-preview")
                        .header("Authorization", "Bearer " + admin))
                .andReturn().getResponse().getStatus()).isEqualTo(403);

        String outsider = verifiedUser("Out Sider", "out@other-" + domain);
        createWorkspace(outsider, "Other Firm");
        assertThat(archive(login("out@other-" + domain), TITLE).getResponse().getStatus()).isEqualTo(404);

        mvc.perform(put("/api/v1/projects/" + projectId + "/members/" + memberIdOf(admin, "alok@" + domain))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"LEAD"}"""))
                .andExpect(status().isOk());
        assertThat(archive(admin, TITLE).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("a client seat never lists an archived position, even when it asks for archived ones")
    void aClientNeverSeesAnArchivedPosition() throws Exception {
        String client = clientOn(projectId);
        assertThat(ids(list(client, ""))).contains(projectId);

        archive(sara, TITLE);

        assertThat(ids(list(client, "?includeArchived=true"))).doesNotContain(projectId);
    }

    @Test
    @DisplayName("the only lead of an archived position may leave the workspace, as of a delivered one")
    void anArchivedPositionDoesNotHoldItsLead() throws Exception {
        String saraMemberId = memberIdOf(admin, "sara@" + domain);
        assertThat(codeOf(mvc.perform(delete("/api/v1/members/" + saraMemberId)
                        .header("Authorization", "Bearer " + admin))
                .andReturn())).isEqualTo("MEMBER_LEADS_PROJECTS");

        archive(sara, TITLE);

        mvc.perform(delete("/api/v1/members/" + saraMemberId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
    }

    private MvcResult archive(String token, String typedName) throws Exception {
        return mvc.perform(post("/api/v1/projects/" + projectId + "/archive")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"confirmName":"%s"}""".formatted(typedName)))
                .andReturn();
    }

    private JsonNode list(String token, String query) throws Exception {
        return body(mvc.perform(get("/api/v1/projects" + query).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
    }

    private List<String> activityTypes() throws Exception {
        List<String> types = new ArrayList<>();
        body(mvc.perform(get("/api/v1/projects/" + projectId + "/activity").header("Authorization", "Bearer " + sara))
                .andExpect(status().isOk())
                .andReturn()).get("entries").forEach(entry -> types.add(entry.get("type").asText()));
        return types;
    }

    private static List<String> ids(JsonNode positions) {
        List<String> ids = new ArrayList<>();
        positions.forEach(position -> ids.add(position.get("id").asText()));
        return ids;
    }
}
