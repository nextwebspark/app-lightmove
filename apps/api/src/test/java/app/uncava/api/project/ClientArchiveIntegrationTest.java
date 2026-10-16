package app.uncava.api.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.uncava.api.IntegrationTest;
import app.uncava.api.candidate.PipelineFlowSupport;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * Archiving a business unit: an admin's call, its name typed back, and only once none of its positions is live —
 * archiving those is their leads'. An archived unit leaves the registry's list and takes no new position.
 */
@IntegrationTest
class ClientArchiveIntegrationTest extends PipelineFlowSupport {

    private String clientId;

    @BeforeEach
    void aBusinessUnit() throws Exception {
        firm("Unit Archive Firm");
        clientId = body(write(admin, "POST", "/api/v1/clients", """
                {"customName":"Retail Banking"}""")).get("id").asText();
    }

    @Test
    @DisplayName("an admin archives a unit by typing its name; the list hides it unless asked, and restore returns it")
    void anAdminArchivesAndRestores() throws Exception {
        MvcResult mistyped = archive(admin, "Retail");
        assertThat(mistyped.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(mistyped)).isEqualTo("ARCHIVE_NAME_MISMATCH");

        JsonNode archived = body(archive(admin, "retail banking"));
        assertThat(archived.get("archivedAt").isNull()).isFalse();
        assertThat(archived.get("archivedByName").asText()).isEqualTo("Alok Kumar");
        assertThat(ids(list(""))).doesNotContain(clientId);
        assertThat(ids(list("?includeArchived=true"))).contains(clientId);

        assertThat(write(admin, "POST", "/api/v1/clients/" + clientId + "/restore", "{}").getResponse().getStatus())
                .isEqualTo(200);
        assertThat(ids(list(""))).contains(clientId);
    }

    @Test
    @DisplayName("a member keeps the registry but may not archive or restore a unit")
    void aMemberMayNotArchive() throws Exception {
        String sara = member("sara");

        assertThat(archive(sara, "Retail Banking").getResponse().getStatus()).isEqualTo(403);
        assertThat(write(sara, "POST", "/api/v1/clients/" + clientId + "/restore", "{}").getResponse().getStatus())
                .isEqualTo(403);
    }

    @Test
    @DisplayName("a unit with a live position is refused, naming it; once its lead archives it, the unit goes")
    void livePositionsComeFirst() throws Exception {
        String projectId = position("Head of Retail");

        MvcResult refused = archive(admin, "Retail Banking");
        assertThat(refused.getResponse().getStatus()).isEqualTo(409);
        assertThat(codeOf(refused)).isEqualTo("CLIENT_HAS_LIVE_POSITIONS");
        assertThat(body(refused).get("positionIds").get(0).asText()).isEqualTo(projectId);

        assertThat(write(admin, "POST", "/api/v1/projects/" + projectId + "/archive", """
                {"confirmName":"Head of Retail"}""").getResponse().getStatus()).isEqualTo(200);
        assertThat(archive(admin, "Retail Banking").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("no position is opened or restored under an archived unit")
    void anArchivedUnitTakesNoPosition() throws Exception {
        String projectId = position("Head of Retail");
        write(admin, "POST", "/api/v1/projects/" + projectId + "/archive", """
                {"confirmName":"Head of Retail"}""");
        archive(admin, "Retail Banking");

        MvcResult opened = write(admin, "POST", "/api/v1/projects", """
                {"clientId":"%s","positionTitle":"Head of Cards"}""".formatted(clientId));
        assertThat(codeOf(opened)).isEqualTo("CLIENT_ARCHIVED");
        MvcResult restored = write(admin, "POST", "/api/v1/projects/" + projectId + "/restore", "{}");
        assertThat(codeOf(restored)).isEqualTo("CLIENT_ARCHIVED");
    }

    @Test
    @DisplayName("an archived unit keeps its name: another of that name is refused with a way back to it")
    void anArchivedUnitKeepsItsName() throws Exception {
        archive(admin, "Retail Banking");

        MvcResult again = write(admin, "POST", "/api/v1/clients", """
                {"customName":"retail banking"}""");
        assertThat(again.getResponse().getStatus()).isEqualTo(409);
        assertThat(codeOf(again)).isEqualTo("CLIENT_NAME_ARCHIVED");
    }

    private MvcResult archive(String token, String typedName) throws Exception {
        return write(token, "POST", "/api/v1/clients/" + clientId + "/archive", """
                {"confirmName":"%s"}""".formatted(typedName));
    }

    private String position(String title) throws Exception {
        MvcResult created = write(admin, "POST", "/api/v1/projects", """
                {"clientId":"%s","positionTitle":"%s"}""".formatted(clientId, title));
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        return body(created).get("id").asText();
    }

    private JsonNode list(String query) throws Exception {
        return body(mvc.perform(get("/api/v1/clients" + query).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
    }

    private static List<String> ids(JsonNode rows) {
        List<String> ids = new ArrayList<>();
        rows.forEach(row -> ids.add(row.get("id").asText()));
        return ids;
    }
}
