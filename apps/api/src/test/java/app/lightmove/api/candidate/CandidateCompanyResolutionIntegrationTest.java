package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import app.lightmove.api.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Where a candidate write lands the person: a save that names no company is not a move, and a typed employer the
 * position holds under exactly that name is that company — so neither leaves someone "not in universe" beside the
 * company they work at.
 */
@IntegrationTest
class CandidateCompanyResolutionIntegrationTest extends PipelineFlowSupport {

    @Test
    @DisplayName("a whole-record save naming no company keeps the person at theirs, at the stage they are on")
    void aSaveNamingNoCompanyIsNotAMove() throws Exception {
        firm("Stale Save Firm");
        String projectId = mandate("Head of Retail");
        String companyId = capture(projectId, "Spinneys Group");
        String candidateId = add(projectId, """
                {"triageCompanyId":"%s","fullName":"Wei Ling Tan"}""".formatted(companyId)).get("id").asText();
        write(admin, "PUT", candidatesUrl(projectId) + "/" + candidateId + "/triage-status", """
                {"status":"declined"}""");

        JsonNode saved = replace(projectId, candidateId, """
                {"fullName":"Wei Ling Tan","title":"Retail Director"}""");

        assertThat(saved.get("triageCompanyId").asText()).isEqualTo(companyId);
        assertThat(saved.get("companyName").asText()).isEqualTo("Spinneys Group");
        assertThat(saved.get("triageStatus").asText()).isEqualTo("declined");
        assertThat(saved.get("title").asText()).isEqualTo("Retail Director");
    }

    @Test
    @DisplayName("naming the company they are at keeps them there; naming another employer moves them off")
    void onlyAnotherEmployerMovesThem() throws Exception {
        firm("Employer Move Firm");
        String projectId = mandate("Head of Retail");
        String companyId = capture(projectId, "Spinneys Group");
        String candidateId = add(projectId, """
                {"triageCompanyId":"%s","fullName":"Wei Ling Tan"}""".formatted(companyId)).get("id").asText();

        assertThat(replace(projectId, candidateId, """
                {"fullName":"Wei Ling Tan","employerName":"spinneys group"}""").get("triageCompanyId").asText())
                .isEqualTo(companyId);

        JsonNode moved = replace(projectId, candidateId, """
                {"fullName":"Wei Ling Tan","employerName":"Outside Holdings"}""");
        assertThat(moved.get("triageCompanyId").isNull()).isTrue();
        assertThat(moved.get("companyName").asText()).isEqualTo("Outside Holdings");
    }

    @Test
    @DisplayName("an executive added with an employer the position holds lands under that company")
    void aTypedEmployerThePositionHoldsIsThatCompany() throws Exception {
        firm("Typed Employer Firm");
        String projectId = mandate("Head of Retail");
        String companyId = capture(projectId, "Spinneys Group");
        write(admin, "PATCH", "/api/v1/projects/" + projectId + "/triage/" + companyId, """
                {"noExecutiveFound":true}""");

        JsonNode added = add(projectId, """
                {"fullName":"Wei Ling Tan","employerName":"SPINNEYS GROUP"}""");

        assertThat(added.get("triageCompanyId").asText()).isEqualTo(companyId);
        assertThat(added.get("companyName").asText()).isEqualTo("Spinneys Group");
        assertThat(db.queryForObject("select no_executive_found from app_lm_project_triage_company where id = ?::uuid",
                Boolean.class, companyId)).isFalse();
        assertThat(body(mvc.perform(get(candidatesUrl(projectId) + "?unmapped=true")
                        .header("Authorization", "Bearer " + admin))
                .andReturn()).get("totalCount").asInt()).isZero();
    }

    @Test
    @DisplayName("an employer the position does not hold stays the person's own, at no company")
    void anEmployerOutsideTheUniverseStaysUnmapped() throws Exception {
        firm("Outside Employer Firm");
        String projectId = mandate("Head of Retail");
        capture(projectId, "Spinneys Group");

        JsonNode added = add(projectId, """
                {"fullName":"Wei Ling Tan","employerName":"Outside Holdings"}""");

        assertThat(added.get("triageCompanyId").isNull()).isTrue();
        assertThat(added.get("companyName").asText()).isEqualTo("Outside Holdings");
    }

    private JsonNode replace(String projectId, String candidateId, String json) throws Exception {
        return body(write(admin, "PUT", candidatesUrl(projectId) + "/" + candidateId, json));
    }
}
