package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * An executive hidden from the client (V131) is absent from every read a client seat can reach — the list,
 * the drawer, the photo, the talent map, the report and the export — and a client seat sees exactly what it
 * saw before while everyone is visible. Staff, and an API key, still read them everywhere.
 */
@IntegrationTest
class ClientVisibilityTest extends PipelineFlowSupport {

    private String projectId;
    private String hidden;
    private String client;
    private String researcher;

    @BeforeEach
    void aPositionWithOneHiddenExecutive() throws Exception {
        firm("Client Visibility Firm");
        projectId = mandate("Chief Financial Officer");
        String company = capture(projectId, "ACWA Power");
        add(projectId, """
                {"triageCompanyId":"%s","fullName":"Shown Person","locationCountry":"United Arab Emirates"}"""
                .formatted(company));
        hidden = add(projectId, """
                {"triageCompanyId":"%s","fullName":"Hidden Person","locationCountry":"United Arab Emirates"}"""
                .formatted(company)).get("id").asText();
        client = clientOn(projectId);
        researcher = researcherOn(projectId, "sara");
    }

    @Test
    @DisplayName("while everyone is visible, a client seat reads every executive, as it always did")
    void everyoneVisibleByDefault() throws Exception {
        assertThat(read(client, candidatesUrl(projectId))).contains("Shown Person", "Hidden Person");
        assertThat(read(client, "/api/v1/projects/" + projectId + "/talent-map")).contains("Hidden Person");
    }

    @Test
    @DisplayName("a hidden executive is absent from every read a client seat reaches, and a 404 by id")
    void aHiddenExecutiveIsAbsentForAClient() throws Exception {
        write(admin, "PUT", candidatesUrl(projectId) + "/" + hidden + "/client-visibility", """
                {"clientVisible":false}""");

        assertThat(read(client, candidatesUrl(projectId))).contains("Shown Person").doesNotContain("Hidden Person");
        assertThat(mvc.perform(get(candidatesUrl(projectId) + "/" + hidden).header("Authorization", "Bearer " + client))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mvc.perform(get(candidatesUrl(projectId) + "/" + hidden + "/photo")
                .header("Authorization", "Bearer " + client)).andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(read(client, "/api/v1/projects/" + projectId + "/talent-map")).contains("Shown Person")
                .doesNotContain("Hidden Person");
        assertThat(read(client, "/api/v1/projects/" + projectId + "/report"))
                .contains("\"executivesMapped\":1");
        assertThat(read(client, "/api/v1/projects/" + projectId + "/export/companies")).contains("Shown Person")
                .doesNotContain("Hidden Person");
    }

    @Test
    @DisplayName("staff and an API key still read a hidden executive everywhere")
    void staffStillSeeThem() throws Exception {
        write(admin, "PUT", candidatesUrl(projectId) + "/" + hidden + "/client-visibility", """
                {"clientVisible":false}""");

        for (String staff : new String[] {admin, researcher}) {
            assertThat(read(staff, candidatesUrl(projectId))).contains("Hidden Person");
            assertThat(read(staff, candidatesUrl(projectId) + "/" + hidden)).contains("\"clientVisible\":false");
            assertThat(read(staff, "/api/v1/projects/" + projectId + "/talent-map")).contains("Hidden Person");
            assertThat(read(staff, "/api/v1/projects/" + projectId + "/report")).contains("\"executivesMapped\":2");
            assertThat(read(staff, "/api/v1/projects/" + projectId + "/export/companies")).contains("Hidden Person");
        }
        String key = body(mvc.perform(post("/api/v1/workspace/api-keys").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Universe","scopes":["companies:read","candidates:read"]}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("secret").asText();
        assertThat(read(key, "/api/v1/public/projects/" + projectId + "/universe")).contains("Hidden Person");
    }

    private String read(String token, String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
