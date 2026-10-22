package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * #903: an executive stands at a Companies stage of their own. Moving one person at a company moves that person
 * alone, and the company is listed on their new stage under them; moving the company moves everyone at it.
 */
@IntegrationTest
class CandidateTriageStatusIntegrationTest extends PipelineFlowSupport {

    @Test
    @DisplayName("declining one executive leaves their colleagues, and their company, in the universe")
    void oneExecutiveMovesAlone() throws Exception {
        firm("Stage Alone Firm");
        String projectId = mandate("Chief Financial Officer");
        String alshaya = capture(projectId, "Alshaya");
        String khaled = executive(projectId, alshaya, "Khaled Al Beirouty");
        executive(projectId, alshaya, "Rana Haddad");
        executive(projectId, alshaya, "Omar Farouk");

        JsonNode moved = body(stage(admin, projectId, khaled, "declined"));
        assertThat(moved.get("triageStatus").asText()).isEqualTo("declined");

        assertThat(companies(projectId, "inUniverse", null)).containsExactly("Alshaya");
        assertThat(companies(projectId, "declined", null)).containsExactly("Alshaya");
        assertThat(people(projectId, alshaya, "inUniverse")).containsExactlyInAnyOrder("Rana Haddad", "Omar Farouk");
        assertThat(people(projectId, alshaya, "declined")).containsExactly("Khaled Al Beirouty");
        assertThat(companies(projectId, "declined", "Rana")).isEmpty();
        assertThat(companies(projectId, "inUniverse", "Khaled")).isEmpty();

        JsonNode counts = triage(projectId, "declined", null).get("counts");
        assertThat(counts.get("inUniverse").asLong()).isEqualTo(1);
        assertThat(counts.get("declined").asLong()).isEqualTo(1);
        assertThat(counts.get("shortlisted").asLong()).isZero();
    }

    @Test
    @DisplayName("moving the company moves everyone mapped at it, and a person filed there later starts at its stage")
    void theCompanyCarriesEveryone() throws Exception {
        firm("Stage Company Firm");
        String projectId = mandate("Chief Financial Officer");
        String alshaya = capture(projectId, "Alshaya");
        String khaled = executive(projectId, alshaya, "Khaled Al Beirouty");
        executive(projectId, alshaya, "Rana Haddad");
        stage(admin, projectId, khaled, "declined");

        assertThat(write(admin, "PATCH", "/api/v1/projects/" + projectId + "/triage/" + alshaya, """
                {"status":"shortlisted"}""").getResponse().getStatus()).isEqualTo(200);

        assertThat(people(projectId, alshaya, "shortlisted")).containsExactlyInAnyOrder("Khaled Al Beirouty",
                "Rana Haddad");
        assertThat(companies(projectId, "inUniverse", null)).isEmpty();
        assertThat(companies(projectId, "declined", null)).isEmpty();
        executive(projectId, alshaya, "Layla Haddad");
        assertThat(people(projectId, alshaya, "shortlisted")).contains("Layla Haddad");
    }

    @Test
    @DisplayName("an unknown stage is a 400, and a client seat cannot move anyone")
    void refusals() throws Exception {
        firm("Stage Refusal Firm");
        String projectId = mandate("Chief Financial Officer");
        String alshaya = capture(projectId, "Alshaya");
        String khaled = executive(projectId, alshaya, "Khaled Al Beirouty");

        assertThat(stage(admin, projectId, khaled, "sideways").getResponse().getStatus()).isEqualTo(400);
        assertThat(stage(clientOn(projectId), projectId, khaled, "declined").getResponse().getStatus())
                .isEqualTo(403);
        assertThat(people(projectId, alshaya, "inUniverse")).containsExactly("Khaled Al Beirouty");
    }

    private String executive(String projectId, String triageCompanyId, String fullName) throws Exception {
        return add(projectId, """
                {"triageCompanyId":"%s","fullName":"%s"}""".formatted(triageCompanyId, fullName))
                .get("id").asText();
    }

    private MvcResult stage(String token, String projectId, String candidateId, String stage) throws Exception {
        return write(token, "PUT", candidatesUrl(projectId) + "/" + candidateId + "/triage-status", """
                {"status":"%s"}""".formatted(stage));
    }

    private JsonNode triage(String projectId, String stage, String executiveQuery) throws Exception {
        var request = get("/api/v1/projects/" + projectId + "/triage").param("status", stage);
        if (executiveQuery != null) {
            request = request.param("executiveQuery", executiveQuery);
        }
        return body(mvc.perform(request.header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
    }

    private List<String> companies(String projectId, String stage, String executiveQuery) throws Exception {
        List<String> names = new ArrayList<>();
        triage(projectId, stage, executiveQuery).get("companies")
                .forEach(company -> names.add(company.get("companyName").asText()));
        return names;
    }

    private List<String> people(String projectId, String triageCompanyId, String stage) throws Exception {
        JsonNode page = body(mvc.perform(get(candidatesUrl(projectId))
                        .param("triageCompanyId", triageCompanyId).param("triageStatus", stage)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
        List<String> names = new ArrayList<>();
        page.get("candidates").forEach(candidate -> names.add(candidate.get("fullName").asText()));
        return names;
    }
}
