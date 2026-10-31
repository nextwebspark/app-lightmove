package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import app.lightmove.api.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * Removing a company from a position takes everyone mapped at it, on every stage, once the caller says it warned
 * about them; removing a company's last executive can take the company too. Nobody is left behind with a company's
 * name and no company.
 */
@IntegrationTest
class CompanyRemovalIntegrationTest extends PipelineFlowSupport {

    @Test
    @DisplayName("a company with executives is not removed unless the caller warned they go with it")
    void refusesARemovalThatWouldTakePeopleUnannounced() throws Exception {
        firm("Unannounced Removal Firm");
        String projectId = mandate("Head of Retail");
        String companyId = capture(projectId, "Spinneys Group");
        executive(projectId, companyId, "Wei Ling Tan");

        MvcResult refused = removeCompany(projectId, companyId, false);

        assertThat(refused.getResponse().getStatus()).isEqualTo(409);
        assertThat(codeOf(refused)).isEqualTo("TRIAGE_COMPANY_HAS_EXECUTIVES");
        assertThat(peopleAt(projectId, companyId)).isEqualTo(1);
    }

    @Test
    @DisplayName("a company removed with its executives takes them all, on every stage, and keeps the people")
    void removingACompanyTakesEveryoneMappedAtIt() throws Exception {
        firm("Company Removal Firm");
        String projectId = mandate("Head of Retail");
        String companyId = capture(projectId, "Spinneys Group");
        JsonNode inUniverse = executive(projectId, companyId, "Wei Ling Tan");
        JsonNode declined = executive(projectId, companyId, "Omar Haddad");
        assertThat(write(admin, "PUT", candidatesUrl(projectId) + "/" + declined.get("id").asText()
                + "/triage-status", """
                {"status":"declined"}""").getResponse().getStatus()).isEqualTo(200);

        assertThat(removeCompany(projectId, companyId, true).getResponse().getStatus()).isEqualTo(204);

        assertThat(everyoneOn(projectId).get("totalCount").asInt()).isZero();
        assertThat(db.queryForObject("select count(*) from app_lm_person where id in (?::uuid, ?::uuid)",
                Integer.class, inUniverse.get("personId").asText(), declined.get("personId").asText()))
                .isEqualTo(2);
        assertThat(db.queryForObject("""
                select count(*) from app_lm_person_activity where project_id = ?::uuid and kind = 'UNMAPPED'""",
                Integer.class, projectId)).isEqualTo(2);
        assertThat(db.queryForObject("""
                select count(*) from app_lm_audit_event where event_type = 'CANDIDATE_REMOVED' and target_id = ?""",
                Integer.class, projectId)).isEqualTo(2);
        assertThat(db.queryForObject("""
                select metadata ->> 'executivesRemoved' from app_lm_audit_event
                where event_type = 'TRIAGE_COMPANY_REMOVED' and target_id = ?""", String.class, projectId))
                .isEqualTo("2");
    }

    @Test
    @DisplayName("a company with nobody at it is removed as it always was, and comes back fresh")
    void anEmptyCompanyNeedsNoWarning() throws Exception {
        firm("Empty Company Removal Firm");
        String projectId = mandate("Head of Retail");
        String companyId = capture(projectId, "Spinneys Group");

        assertThat(removeCompany(projectId, companyId, false).getResponse().getStatus()).isEqualTo(204);

        String returned = capture(projectId, "Spinneys Group");
        assertThat(returned).isNotEqualTo(companyId);
        assertThat(peopleAt(projectId, returned)).isZero();
    }

    @Test
    @DisplayName("removing the only executive at a company with the box ticked removes the company too")
    void theLastExecutiveCanTakeTheirCompany() throws Exception {
        firm("Last Executive Firm");
        String projectId = mandate("Head of Retail");
        String companyId = capture(projectId, "Spinneys Group");
        String candidateId = executive(projectId, companyId, "Wei Ling Tan").get("id").asText();

        assertThat(removeCandidate(projectId, candidateId, true).getResponse().getStatus()).isEqualTo(204);

        assertThat(everyoneOn(projectId).get("totalCount").asInt()).isZero();
        assertThat(db.queryForObject("select count(*) from app_lm_project_triage_company where id = ?::uuid",
                Integer.class, companyId)).isZero();
    }

    @Test
    @DisplayName("the company stays when someone else is at it, on any stage, and so does the person")
    void theCompanyStaysWhileSomeoneElseIsThere() throws Exception {
        firm("Shared Company Firm");
        String projectId = mandate("Head of Retail");
        String companyId = capture(projectId, "Spinneys Group");
        String candidateId = executive(projectId, companyId, "Wei Ling Tan").get("id").asText();
        String colleague = executive(projectId, companyId, "Omar Haddad").get("id").asText();
        write(admin, "PUT", candidatesUrl(projectId) + "/" + colleague + "/triage-status", """
                {"status":"declined"}""");

        MvcResult refused = removeCandidate(projectId, candidateId, true);

        assertThat(refused.getResponse().getStatus()).isEqualTo(409);
        assertThat(codeOf(refused)).isEqualTo("TRIAGE_COMPANY_HAS_EXECUTIVES");
        assertThat(peopleAt(projectId, companyId)).isEqualTo(2);
    }

    @Test
    @DisplayName("removing a person without the box ticked leaves their company in universe with nobody at it")
    void anUntickedRemovalKeepsTheCompany() throws Exception {
        firm("Kept Company Firm");
        String projectId = mandate("Head of Retail");
        String companyId = capture(projectId, "Spinneys Group");
        String candidateId = executive(projectId, companyId, "Wei Ling Tan").get("id").asText();

        assertThat(removeCandidate(projectId, candidateId, false).getResponse().getStatus()).isEqualTo(204);

        assertThat(db.queryForObject("select status from app_lm_project_triage_company where id = ?::uuid",
                String.class, companyId)).isEqualTo("IN_UNIVERSE");
        assertThat(peopleAt(projectId, companyId)).isZero();
    }

    private JsonNode executive(String projectId, String companyId, String fullName) throws Exception {
        return add(projectId, """
                {"triageCompanyId":"%s","fullName":"%s"}""".formatted(companyId, fullName));
    }

    private MvcResult removeCompany(String projectId, String companyId, boolean withExecutives) throws Exception {
        return mvc.perform(delete("/api/v1/projects/" + projectId + "/triage/" + companyId
                        + (withExecutives ? "?withExecutives=true" : ""))
                        .header("Authorization", "Bearer " + admin))
                .andReturn();
    }

    private MvcResult removeCandidate(String projectId, String candidateId, boolean withCompany) throws Exception {
        return mvc.perform(delete(candidatesUrl(projectId) + "/" + candidateId
                        + (withCompany ? "?withCompany=true" : ""))
                        .header("Authorization", "Bearer " + admin))
                .andReturn();
    }

    private int peopleAt(String projectId, String companyId) throws Exception {
        return body(mvc.perform(get(candidatesUrl(projectId) + "?triageCompanyId=" + companyId)
                        .header("Authorization", "Bearer " + admin))
                .andReturn()).get("totalCount").asInt();
    }

    private JsonNode everyoneOn(String projectId) throws Exception {
        return body(mvc.perform(get(candidatesUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andReturn());
    }
}
