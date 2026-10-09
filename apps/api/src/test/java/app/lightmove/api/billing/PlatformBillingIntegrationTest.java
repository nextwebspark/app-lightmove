package app.lightmove.api.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/** A platform admin granting credits and setting an invoiced workspace's plan, and nobody else doing either. */
@IntegrationTest
class PlatformBillingIntegrationTest extends BillingFlowSupport {

    @Test
    @DisplayName("a platform grant shows in the workspace's balance and in the audit trail")
    void platformGrantReachesTheBalanceAndTheAuditTrail() throws Exception {
        UUID workspace = newWorkspace();
        String admin = superAdmin();

        JsonNode granted = body(grantCredits(admin, workspace, """
                {"source":"MANUAL","credits":100,"filsPerCredit":150,"externalRef":"BANK-2026-10-07"}
                """).andExpect(status().isCreated()).andReturn());

        assertThat(granted.get("available").asLong()).isEqualTo(100);
        assertThat(ledger.balanceOf(workspace).available()).isEqualTo(100);
        assertThat(db.queryForObject("""
                SELECT count(*) FROM app_lm_audit_event
                WHERE event_type = 'CREDITS_GRANTED' AND workspace_id = ?""", Long.class, workspace)).isEqualTo(1);

        JsonNode resent = body(grantCredits(admin, workspace, """
                {"source":"MANUAL","credits":100,"filsPerCredit":150,"externalRef":"BANK-2026-10-07"}
                """).andReturn());
        assertThat(resent.get("alreadyGranted").asBoolean()).isTrue();
        assertThat(ledger.balanceOf(workspace).available()).isEqualTo(100);
        assertLedgerAddsUp(workspace);
    }

    @Test
    @DisplayName("a workspace admin cannot grant credits or set a plan, even on their own workspace, and is not told the routes exist")
    void workspaceAdminIsRefused() throws Exception {
        String ownerEmail = "admin@" + domain;
        UUID workspace = UUID.fromString(createWorkspace(verifiedUser("Omar Khalil", ownerEmail), "Own Firm"));
        String owner = login(ownerEmail);

        grantCredits(owner, workspace, """
                {"source":"PROMO","credits":1000}
                """).andExpect(status().isNotFound());
        setSubscription(owner, workspace, """
                {"plan":"PRO","billingInterval":"ANNUAL","seats":5}
                """).andExpect(status().isNotFound());
        assertThat(ledger.balanceOf(workspace).available()).isZero();
    }

    @Test
    @DisplayName("credits a workspace pays for through Stripe cannot be granted by hand")
    void boughtCreditsAreNotGrantedByHand() throws Exception {
        MvcResult refused = grantCredits(superAdmin(), newWorkspace(), """
                {"source":"PURCHASED","credits":500}
                """).andExpect(status().isBadRequest()).andReturn();
        assertThat(codeOf(refused)).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("a manual grant must state its cost per credit, and a free one must not claim one")
    void costBasisFollowsTheSource() throws Exception {
        String admin = superAdmin();
        UUID workspace = newWorkspace();

        assertThat(codeOf(grantCredits(admin, workspace, """
                {"source":"MANUAL","credits":100}
                """).andExpect(status().isBadRequest()).andReturn())).isEqualTo("VALIDATION_FAILED");
        assertThat(codeOf(grantCredits(admin, workspace, """
                {"source":"GOODWILL","credits":20,"filsPerCredit":150}
                """).andExpect(status().isBadRequest()).andReturn())).isEqualTo("VALIDATION_FAILED");
        grantCredits(admin, workspace, """
                {"source":"GOODWILL","credits":20}
                """).andExpect(status().isCreated());
        assertThat(ledger.balanceOf(workspace).available()).isEqualTo(20);
    }

    @Test
    @DisplayName("an invoiced workspace's plan and seats set its monthly contact credits")
    void invoicedSubscriptionSetsMonthlyCredits() throws Exception {
        UUID workspace = newWorkspace();
        String admin = superAdmin();

        JsonNode pro = body(setSubscription(admin, workspace, """
                {"plan":"PRO","billingInterval":"ANNUAL","seats":3}
                """).andExpect(status().isOk()).andReturn());
        assertThat(pro.get("monthlyContactCredits").asLong()).isEqualTo(450);
        assertThat(pro.get("status").asText()).isEqualTo("INVOICED");

        setSubscription(admin, workspace, """
                {"plan":"ENTERPRISE","billingInterval":"ANNUAL","seats":12}
                """).andExpect(status().isBadRequest());
        setSubscription(admin, workspace, """
                {"plan":"PRO","billingInterval":"ANNUAL","seats":3,"contactCreditPool":2500}
                """).andExpect(status().isBadRequest());
        JsonNode enterprise = body(setSubscription(admin, workspace, """
                {"plan":"ENTERPRISE","billingInterval":"ANNUAL","seats":12,"contactCreditPool":2500}
                """).andExpect(status().isOk()).andReturn());
        assertThat(enterprise.get("monthlyContactCredits").asLong()).isEqualTo(2500);
        assertThat(db.queryForObject("""
                SELECT count(*) FROM app_lm_audit_event
                WHERE event_type = 'INVOICED_SUBSCRIPTION_SET' AND workspace_id = ?""", Long.class, workspace))
                .isEqualTo(2);
    }

    private ResultActions grantCredits(String token, UUID workspace, String body)
            throws Exception {
        return mvc.perform(post("/api/v1/platform/workspaces/" + workspace + "/credit-grants")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions setSubscription(String token, UUID workspace,
                                                                              String body) throws Exception {
        return mvc.perform(put("/api/v1/platform/workspaces/" + workspace + "/subscription")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
