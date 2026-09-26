package app.lightmove.api.workspace;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The budget on founding workspaces, shared by signup's door and Settings'. Its own class because
 * {@code application-test.yml} turns rate limiting off for the suite; see {@code ExtensionPairingBudgetTest}.
 */
@IntegrationTest
@TestPropertySource(properties = {
        "lightmove.auth.rate-limit.enabled=true",
        "lightmove.auth.rate-limit.workspace-creations-per-hour=2"
})
class WorkspaceCreationBudgetTest extends FlowTestSupport {

    @Test
    @DisplayName("founding workspaces is refused once the account's budget is spent, across both doors")
    void creationIsRefusedOnceTheBudgetIsSpent() throws Exception {
        String alokEmail = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alokEmail), "First Firm");
        String token = login(alokEmail);

        create(token, "Second Firm").andExpect(status().isCreated());
        create(token, "Third Firm").andExpect(status().isTooManyRequests());
    }

    private ResultActions create(String bearerToken, String name) throws Exception {
        return mvc.perform(post("/api/v1/workspaces")
                .header("Authorization", "Bearer " + bearerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","companySize":"11-50 people","primaryRegion":"GCC",
                         "teamFocus":"Executive search"}
                        """.formatted(name)));
    }
}
