package app.lightmove.api.companydiscovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * What a firm's day of AI Research costs, and what happens when it is gone.
 *
 * <p>The cap is set to three here rather than the shipped twenty-five, because the assertion is
 * about the boundary and not the number.
 */
@IntegrationTest
@TestPropertySource(properties = "lightmove.company.discovery.daily-searches-per-workspace=3")
class CompanyDiscoverySpendIntegrationTest extends FlowTestSupport {

    private static final String DISCOVER_URL = "/api/v1/companies/discover";

    @Autowired JdbcTemplate db;

    private String adminToken;

    @Test
    @DisplayName("the day runs out, and the refusal costs the provider nothing")
    void theDayRunsOut() throws Exception {
        workspace("Daily Cap Firm");

        for (int search = 1; search <= 3; search++) {
            JsonNode answer = body(search(adminToken).andExpect(status().isOk()).andReturn());
            assertThat(answer.get("searchesLeftToday").asInt()).isEqualTo(3 - search);
        }

        search(adminToken).andExpect(status().isTooManyRequests());

        // Refused before the provider was reached: the fourth question was never asked.
        assertThat(discovery.questionsAsked()).hasSize(3);
    }

    @Test
    @DisplayName("hitting the cap is audited as a failure that names why")
    void theCapIsAudited() throws Exception {
        workspace("Audited Cap Firm");
        for (int search = 0; search < 3; search++) {
            search(adminToken).andExpect(status().isOk());
        }

        search(adminToken).andExpect(status().isTooManyRequests());

        List<Map<String, Object>> events = db.queryForList(
                "SELECT outcome, metadata ->> 'reason' AS reason FROM app_lm_audit_event"
                        + " WHERE event_type = ? ORDER BY id", "COMPANY_DISCOVERY_RAN");
        assertThat(events).hasSize(4);
        // Three that ran and one that did not, and the last one says which wall it hit.
        assertThat(events.getLast()).containsEntry("outcome", "FAILURE");
        assertThat(events.getLast()).containsEntry("reason", "daily_cap");
    }

    @Test
    @DisplayName("one firm spending its day does not touch another's")
    void theBudgetIsPerWorkspace() throws Exception {
        workspace("First Firm");
        String first = adminToken;
        for (int search = 0; search < 3; search++) {
            search(first).andExpect(status().isOk());
        }
        search(first).andExpect(status().isTooManyRequests());

        String second = secondWorkspace("Second Firm");
        search(second).andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions search(String token) throws Exception {
        return mvc.perform(post(DISCOVER_URL)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"question":"Who are the large IPPs in the Gulf?"}"""));
    }

    private void workspace(String firmName) throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), firmName);
        adminToken = login(alok);
    }

    /** Its own user, because one user holds at most one active workspace. */
    private String secondWorkspace(String firmName) throws Exception {
        String sara = "sara@second-" + domain;
        createWorkspace(verifiedUser("Sara Al-Mansour", sara), firmName);
        return login(sara);
    }
}
