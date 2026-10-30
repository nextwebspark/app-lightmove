package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * The plugin asks whether a position already holds the profile or company page it stands on, so it opens the record
 * rather than offering a form whose save would be refused.
 */
@IntegrationTest
class HeldLookupIntegrationTest extends PipelineFlowSupport {

    @Test
    @DisplayName("a profile the position maps is found on its slug, however the address spells it, with who filed it")
    void aHeldProfileIsFound() throws Exception {
        firm("Held Person Firm");
        String projectId = mandate("Chief Financial Officer");
        String faisal = add(projectId, """
                {"fullName":"Faisal Al Nuaimi","linkedinUrl":"https://www.linkedin.com/in/faisal-al-nuaimi/"}""")
                .get("id").asText();

        JsonNode held = body(heldPerson(admin, projectId, "https://linkedin.com/in/Faisal-Al-Nuaimi?trk=feed"));
        assertThat(held.at("/candidate/id").asText()).isEqualTo(faisal);
        assertThat(held.at("/candidate/fullName").asText()).isEqualTo("Faisal Al Nuaimi");
        assertThat(held.at("/addedBy/name").asText()).isEqualTo("Alok Kumar");
        assertThat(held.has("companyLogoUrl")).isTrue();

        assertThat(heldPerson(admin, projectId, "https://www.linkedin.com/in/faisal").getResponse().getStatus())
                .isEqualTo(204);
        assertThat(heldPerson(admin, projectId, "https://example.com/in/faisal-al-nuaimi").getResponse().getStatus())
                .isEqualTo(204);
        String other = mandate("Group Treasurer");
        assertThat(heldPerson(admin, other, "https://www.linkedin.com/in/faisal-al-nuaimi/").getResponse().getStatus())
                .isEqualTo(204);
    }

    @Test
    @DisplayName("a company is found on its LinkedIn page, else on the name a capture would be refused under")
    void aHeldCompanyIsFound() throws Exception {
        firm("Held Company Firm");
        String projectId = mandate("Chief Financial Officer");
        String acwa = body(mvc.perform(post("/api/v1/projects/" + projectId + "/triage/capture")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"ACWA Power","companyLinkedinUrl":"https://www.linkedin.com/company/acwa-power/"}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        String alshaya = capture(projectId, "Alshaya");

        JsonNode byPage = body(heldCompany(admin, projectId, "https://linkedin.com/company/ACWA-Power/about/", "Acwa"));
        assertThat(byPage.get("id").asText()).isEqualTo(acwa);
        assertThat(byPage.get("addedByName").asText()).isEqualTo("Alok Kumar");

        JsonNode byName = body(heldCompany(admin, projectId, "https://www.linkedin.com/company/alshaya-group/", "alshaya"));
        assertThat(byName.get("id").asText()).isEqualTo(alshaya);

        assertThat(heldCompany(admin, projectId, "https://www.linkedin.com/company/acwa/", "Masdar")
                .getResponse().getStatus()).isEqualTo(204);
    }

    @Test
    @DisplayName("a client seat may not ask, and another workspace's position is not found")
    void strangersAreRefused() throws Exception {
        firm("Held Refusal Firm");
        String projectId = mandate("Chief Financial Officer");
        add(projectId, """
                {"fullName":"Faisal Al Nuaimi","linkedinUrl":"https://www.linkedin.com/in/faisal-al-nuaimi/"}""");
        String profile = "https://www.linkedin.com/in/faisal-al-nuaimi/";

        String client = clientOn(projectId);
        assertThat(heldPerson(client, projectId, profile).getResponse().getStatus()).isEqualTo(403);
        assertThat(heldCompany(client, projectId, null, "Alshaya").getResponse().getStatus()).isEqualTo(403);

        String strangerAddress = "stranger@other-" + domain;
        createWorkspace(verifiedUser("Stranger", strangerAddress), "Held Stranger Firm");
        String stranger = login(strangerAddress);
        assertThat(heldPerson(stranger, projectId, profile).getResponse().getStatus()).isEqualTo(404);
        assertThat(heldCompany(stranger, projectId, null, "Alshaya").getResponse().getStatus()).isEqualTo(404);
    }

    private MvcResult heldPerson(String token, String projectId, String linkedinUrl) throws Exception {
        return mvc.perform(get(candidatesUrl(projectId) + "/held")
                        .param("linkedinUrl", linkedinUrl)
                        .header("Authorization", "Bearer " + token))
                .andReturn();
    }

    private MvcResult heldCompany(String token, String projectId, String linkedinUrl, String name) throws Exception {
        var request = get("/api/v1/projects/" + projectId + "/triage/held")
                .param("name", name)
                .header("Authorization", "Bearer " + token);
        if (linkedinUrl != null) {
            request.param("linkedinUrl", linkedinUrl);
        }
        return mvc.perform(request).andReturn();
    }
}
