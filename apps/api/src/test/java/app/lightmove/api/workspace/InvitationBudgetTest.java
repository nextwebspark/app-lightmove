package app.lightmove.api.workspace;

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
import org.springframework.test.web.servlet.ResultActions;

/**
 * The budgets on invitation emails: a workspace's per hour, and a recipient's across every workspace. Its own class
 * because {@code application-test.yml} turns rate limiting off for the suite; see {@code WorkspaceCreationBudgetTest}.
 */
@IntegrationTest
@TestPropertySource(properties = {
        "lightmove.auth.rate-limit.enabled=true",
        "lightmove.auth.rate-limit.invitations-per-hour-per-workspace=3",
        "lightmove.auth.rate-limit.invitations-per-hour-per-recipient=2",
        "lightmove.auth.rate-limit.workspace-creations-per-hour=100",
        "lightmove.auth.rate-limit.signup-attempts-per-hour=100",
        "lightmove.auth.rate-limit.login-attempts-per-minute=100"
})
class InvitationBudgetTest extends FlowTestSupport {

    @Test
    @DisplayName("a batch that would pass the workspace's budget is refused whole, and nobody is emailed")
    void workspaceBudgetRefusesTheWholeBatch() throws Exception {
        createWorkspace(verifiedUser("Alok Kumar", "alok@" + domain), "Busy Firm");
        String admin = login("alok@" + domain);

        invite(admin, "one@" + domain, "two@" + domain).andExpect(status().isOk());
        email.clear();

        invite(admin, "three@" + domain, "four@" + domain).andExpect(status().isTooManyRequests());
        assertThat(email.sent()).isEmpty();
    }

    @Test
    @DisplayName("resending to one address stops once that address's budget is spent")
    void recipientBudgetStopsAResendFlood() throws Exception {
        createWorkspace(verifiedUser("Alok Kumar", "alok@" + domain), "Resend Firm");
        String admin = login("alok@" + domain);
        String target = "target@" + domain;
        invite(admin, target).andExpect(status().isOk());
        String invitationId = body(mvc.perform(get("/api/v1/invitations").header("Authorization", "Bearer " + admin))
                .andReturn()).get(0).get("id").asText();

        resend(admin, invitationId).andExpect(status().isNoContent());
        resend(admin, invitationId).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("one inbox cannot be flooded from many workspaces")
    void recipientBudgetHoldsAcrossWorkspaces() throws Exception {
        String target = "victim@" + domain;
        for (String founder : new String[] {"a", "b"}) {
            createWorkspace(verifiedUser("Founder " + founder, founder + "@" + domain), "Firm " + founder);
            invite(login(founder + "@" + domain), target).andExpect(status().isOk());
        }
        createWorkspace(verifiedUser("Founder c", "c@" + domain), "Firm c");
        invite(login("c@" + domain), target).andExpect(status().isTooManyRequests());
    }

    private ResultActions invite(String bearerToken, String... addresses) throws Exception {
        StringBuilder body = new StringBuilder("[");
        for (int index = 0; index < addresses.length; index++) {
            body.append(index == 0 ? "" : ",").append("{\"email\":\"%s\"}".formatted(addresses[index]));
        }
        return mvc.perform(post("/api/v1/invitations")
                .header("Authorization", "Bearer " + bearerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.append("]").toString()));
    }

    private ResultActions resend(String bearerToken, String invitationId) throws Exception {
        return mvc.perform(post("/api/v1/invitations/" + invitationId + "/resend")
                .header("Authorization", "Bearer " + bearerToken));
    }
}
