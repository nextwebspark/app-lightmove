package app.lightmove.api.publicapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/** A universe past the export cap is refused with its own code, never cut short. */
@IntegrationTest
@TestPropertySource(properties = "lightmove.export.max-companies=1")
class PublicApiUniverseCapIntegrationTest extends FlowTestSupport {

    @Test
    @DisplayName("a stage past the cap is 400 PUBLIC_API_UNIVERSE_TOO_LARGE; one within it reads whole")
    void refusedPastTheCap() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Cap Firm");
        String admin = login(alok);
        String clientId = body(created(admin, "/api/v1/clients", """
                {"customName":"Cap Client"}""")).get("id").asText();
        String projectId = body(created(admin, "/api/v1/projects", """
                {"clientId":"%s","positionTitle":"Head of Retail"}""".formatted(clientId))).get("id").asText();
        String secret = body(created(admin, "/api/v1/workspace/api-keys", """
                {"name":"Universe","scopes":["companies:read","candidates:read"]}""")).get("secret").asText();
        String url = "/api/v1/public/projects/" + projectId + "/universe";

        created(admin, "/api/v1/projects/" + projectId + "/triage/capture", """
                {"companyName":"ACWA Power"}""");
        mvc.perform(get(url).header("Authorization", "Bearer " + secret)).andExpect(status().isOk());

        created(admin, "/api/v1/projects/" + projectId + "/triage/capture", """
                {"companyName":"Masdar"}""");
        MvcResult refused = mvc.perform(get(url).header("Authorization", "Bearer " + secret))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(codeOf(refused)).isEqualTo("PUBLIC_API_UNIVERSE_TOO_LARGE");
        assertThat(body(refused).get("detail").asText()).contains("2 companies").contains("limit of 1");
    }

    private MvcResult created(String bearer, String url, String requestBody) throws Exception {
        return mvc.perform(post(url)
                        .header("Authorization", "Bearer " + bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isCreated()).andReturn();
    }
}
