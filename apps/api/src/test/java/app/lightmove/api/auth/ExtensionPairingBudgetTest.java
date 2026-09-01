package app.lightmove.api.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The budget on minting extension tokens — the only thing between a stolen in-memory access token and
 * a farm of 14-day refresh tokens.
 *
 * <p>Its own class because {@code application-test.yml} turns rate limiting <b>off</b> for the whole
 * suite, and must: nearly every test signs up and logs in repeatedly from one address, and would spend
 * the signup budget rather than testing what it came to test. So this class opts in alone, and keeps
 * its own signups few enough to stay inside the budgets it has just switched on.
 *
 * <p>The account budget is the tight one; the per-IP budget is deliberately far larger, because the
 * threat here is one stolen token rather than one host, and an executive-search firm is a dozen
 * consultants installing the extension from behind a single office NAT on the morning it ships.
 */
@IntegrationTest
@TestPropertySource(properties = {
        "lightmove.auth.rate-limit.enabled=true",
        "lightmove.auth.rate-limit.extension-pairings-per-hour=2",
        "lightmove.auth.rate-limit.extension-pairings-per-hour-per-ip=50"
})
@Import(RecordingEmailSender.Config.class)
class ExtensionPairingBudgetTest extends FlowTestSupport {

    @Test
    @DisplayName("pairing is refused once the account's budget is spent")
    void pairingIsRefusedOnceTheBudgetIsSpent() throws Exception {
        String workspaceOwner = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", workspaceOwner), "Pairing Budget Firm");
        String browserToken = login(workspaceOwner);

        mintExtensionToken(browserToken).andExpect(status().isCreated());
        mintExtensionToken(browserToken).andExpect(status().isCreated());
        mintExtensionToken(browserToken).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("one account's budget does not spend a colleague's")
    void oneAccountsBudgetDoesNotSpendAnothers() throws Exception {
        String firstColleague = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", firstColleague), "Shared Office Firm");
        String firstToken = login(firstColleague);

        String secondColleague = "priya@" + domain;
        inviteAndAccept(firstToken, "Priya Nair", secondColleague, "MEMBER");

        // Both reach MockMvc from the same address, so were the two budgets still one figure the
        // second consultant would inherit an exhausted one — the office-NAT failure the separate
        // per-IP ceiling exists to avoid.
        mintExtensionToken(firstToken).andExpect(status().isCreated());
        mintExtensionToken(firstToken).andExpect(status().isCreated());
        mintExtensionToken(firstToken).andExpect(status().isTooManyRequests());

        mintExtensionToken(login(secondColleague)).andExpect(status().isCreated());
    }

    private ResultActions mintExtensionToken(String bearerToken) throws Exception {
        return mvc.perform(post("/api/v1/auth/extension/tokens")
                .header("Authorization", "Bearer " + bearerToken));
    }
}
