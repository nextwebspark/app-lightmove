package app.lightmove.api.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.TestWorkspaceScopes;
import app.lightmove.api.billing.credit.constant.CreditGrantSource;
import app.lightmove.api.billing.usage.constant.UsageKind;
import app.lightmove.api.billing.usage.service.FairUseGuard;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/** Billing switched off for the deployment, as the demo runs it: nothing refused, nothing emailed, the read says so. */
@IntegrationTest
class BillingSwitchedOffIntegrationTest extends BillingFlowSupport {

    private FairUseGuard fairUse;

    @Autowired
    void scopeTheGuard(FairUseGuard guard) {
        fairUse = TestWorkspaceScopes.scopedByArguments(guard);
    }

    @Test
    @DisplayName("a use far past the month's fair-use ceiling is let through")
    void fairUseRefusesNothing() throws Exception {
        UUID workspaceId = newWorkspace();
        db.update("""
                INSERT INTO app_lm_usage_event (workspace_id, kind, units, est_cost_fils)
                VALUES (?, 'AI_ENRICH', 1000000, 0)""", workspaceId);

        assertThatCode(() -> fairUse.check(workspaceId, null, UsageKind.AI_ENRICH, 1)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("spending every credit emails no billing manager, and the billing read says billing is off")
    void creditsRunningOutEmailNobody() throws Exception {
        String owner = "owner-off@" + domain;
        UUID workspaceId = UUID.fromString(createWorkspace(verifiedUser("Yara Haddad", owner), "Demo Firm"));
        int emailsBefore = email.subjectsFor(owner).size();
        grant(workspaceId, CreditGrantSource.PLAN, 20, Duration.ofDays(20));

        for (int i = 1; i <= 5; i++) {
            ledger.charge(phoneFound(workspaceId, "phone-" + i));
        }

        assertThat(email.subjectsFor(owner)).hasSize(emailsBefore);
        JsonNode billing = body(mvc.perform(get("/api/v1/billing").header("Authorization", "Bearer " + login(owner)))
                .andExpect(status().isOk()).andReturn());
        assertThat(billing.get("enforced").asBoolean()).isFalse();
    }
}
