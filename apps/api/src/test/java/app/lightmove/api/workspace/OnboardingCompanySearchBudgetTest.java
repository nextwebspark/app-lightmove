package app.lightmove.api.workspace;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The budget on signup's company typeahead, which scans the universe for any verified session.
 *
 * <p>Its own class because {@code application-test.yml} turns rate limiting off for the suite; see
 * {@code ExtensionPairingBudgetTest}.
 */
@IntegrationTest
@TestPropertySource(properties = {
        "lightmove.auth.rate-limit.enabled=true",
        "lightmove.auth.rate-limit.onboarding-company-searches-per-minute=2"
})
class OnboardingCompanySearchBudgetTest extends FlowTestSupport {

    @Test
    @DisplayName("the typeahead is refused once the account's budget is spent")
    void searchIsRefusedOnceTheBudgetIsSpent() throws Exception {
        String token = verifiedUser("Alok Kumar", "alok@" + domain);

        search(token).andExpect(status().isOk());
        search(token).andExpect(status().isOk());
        search(token).andExpect(status().isTooManyRequests());
    }

    private ResultActions search(String bearerToken) throws Exception {
        return mvc.perform(get("/api/v1/onboarding/companies").param("q", "acme")
                .header("Authorization", "Bearer " + bearerToken));
    }
}
