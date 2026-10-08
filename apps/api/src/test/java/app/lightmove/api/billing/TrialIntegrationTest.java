package app.lightmove.api.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingPaymentGateway;
import app.lightmove.api.billing.credit.service.MonthlyCreditReset;
import app.lightmove.api.billing.notice.service.BillingNotices;
import app.lightmove.api.billing.payment.model.PaymentEvent;
import app.lightmove.api.billing.payment.model.StripeSubscriptionState;
import app.lightmove.api.billing.plan.constant.SubscriptionStatus;
import app.lightmove.api.billing.usage.constant.UsageKind;
import app.lightmove.api.billing.usage.service.FairUseGuard;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

/** A newly founded workspace's trial: Pro with a few credits and no card, locked once it ends unpaid. */
@IntegrationTest
@TestPropertySource(properties = {"lightmove.billing.enforce=true", "lightmove.billing.trial.enabled=true"})
class TrialIntegrationTest extends BillingFlowSupport {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired private FairUseGuard fairUse;
    @Autowired private MonthlyCreditReset reset;
    @Autowired private BillingNotices notices;
    @Autowired private RecordingPaymentGateway stripe;

    @Test
    @DisplayName("founding a workspace starts a fourteen-day Pro trial with fifty contact credits")
    void foundingStartsATrial() throws Exception {
        Firm firm = newFirm();

        JsonNode billing = billingOf(firm);

        assertThat(billing.at("/plan/code").asText()).isEqualTo("PRO");
        assertThat(billing.get("status").asText()).isEqualTo("TRIALING");
        assertThat(billing.get("seats").asInt()).isEqualTo(1);
        assertThat(Instant.parse(billing.get("trialEndsAt").asText()))
                .isBetween(Instant.now().plus(Duration.ofDays(14)).minusSeconds(60),
                        Instant.now().plus(Duration.ofDays(14)));
        assertThat(billing.at("/credits/monthly").asLong()).isEqualTo(50);
        assertThat(billing.at("/credits/left").asLong()).isEqualTo(50);
        assertThat(billing.at("/credits/resetsAt").asText()).isEqualTo(billing.get("trialEndsAt").asText());
        assertThat(billing.at("/paymentMethod/kind").asText()).isEqualTo("NONE");
        ledger.hold(emailFound(firm.workspaceId(), "trial-find"));
    }

    @Test
    @DisplayName("a founder's second workspace starts with its trial already ended, and no credits")
    void oneTrialPerFounder() throws Exception {
        Firm firm = newFirm();
        JsonNode created = body(mvc.perform(post("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + firm.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"COMPANY","name":"Second Trial Firm"}"""))
                .andExpect(status().isCreated())
                .andReturn());
        UUID second = UUID.fromString(created.at("/workspace/id").asText());

        assertThat(db.queryForObject("SELECT count(*) FROM app_lm_credit_grant WHERE workspace_id = ?", Long.class,
                second)).isZero();
        assertRefusedAsEnded(() -> ledger.hold(emailFound(second, "second-find")));
    }

    @Test
    @DisplayName("once the trial ends unpaid, contact finds, search and AI are refused and nothing is spent")
    void anEndedTrialLocksPaidFeatures() throws Exception {
        Firm firm = newFirm();
        endTrial(firm.workspaceId());
        long entries = entriesOf(firm.workspaceId());

        assertRefusedAsEnded(() -> ledger.hold(emailFound(firm.workspaceId(), "ended-find")));
        assertRefusedAsEnded(() -> ledger.charge(phoneFound(firm.workspaceId(), "ended-charge")));
        assertRefusedAsEnded(() -> fairUse.check(firm.workspaceId(), null, UsageKind.PEOPLE_SEARCH_PAGE, 1));
        assertRefusedAsEnded(() -> fairUse.check(firm.workspaceId(), null, UsageKind.ASSISTANT_ASK, 1));
        assertThat(entriesOf(firm.workspaceId())).isEqualTo(entries);
        assertThat(billingOf(firm).get("status").asText()).isEqualTo("TRIALING");
    }

    @Test
    @DisplayName("the monthly reset grants a trial no month of Pro credits")
    void theResetSkipsATrial() throws Exception {
        Firm firm = newFirm();

        reset.resetAt(Instant.now());

        assertThat(db.queryForList("SELECT external_ref FROM app_lm_credit_grant WHERE workspace_id = ?", String.class,
                firm.workspaceId())).containsExactly("trial:" + firm.workspaceId());
    }

    @Test
    @DisplayName("paying through Checkout converts an ended trial and lifts the lock")
    void checkoutConvertsTheTrial() throws Exception {
        Firm firm = newFirm();
        endTrial(firm.workspaceId());
        mvc.perform(post("/api/v1/billing/checkout/subscription")
                        .header("Authorization", "Bearer " + firm.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"planCode":"CORE","interval":"MONTHLY"}"""))
                .andExpect(status().isOk());
        String customer = stripe.subscriptionCheckoutsOf(firm.workspaceId()).getFirst().customerId();
        String subscriptionId = "sub_trial_" + SEQUENCE.incrementAndGet();
        Instant start = Instant.now().minus(Duration.ofMinutes(5)).truncatedTo(ChronoUnit.SECONDS);
        deliver(new PaymentEvent.InvoicePaid("evt_" + UUID.randomUUID(), "invoice.paid", start.plusSeconds(1),
                "in_" + UUID.randomUUID(), customer, subscriptionId,
                new StripeSubscriptionState(customer, subscriptionId, SubscriptionStatus.ACTIVE,
                        "price_test_core_monthly", 1, start, start.atZone(ZoneOffset.UTC).plusMonths(1).toInstant()),
                true));

        JsonNode billing = billingOf(firm);
        assertThat(billing.at("/plan/code").asText()).isEqualTo("CORE");
        assertThat(billing.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(billing.get("trialEndsAt").isNull()).isTrue();
        assertThat(billing.at("/paymentMethod/kind").asText()).isEqualTo("CARD");
        ledger.hold(emailFound(firm.workspaceId(), "paid-find"));
        fairUse.check(firm.workspaceId(), null, UsageKind.PEOPLE_SEARCH_PAGE, 1);
    }

    @Test
    @DisplayName("billing managers are told once a trial is about to end, and once it has")
    void announcesTheEndOnce() throws Exception {
        Firm firm = newFirm();
        int before = email.subjectsFor(firm.owner()).size();

        setTrialEnd(firm.workspaceId(), Instant.now().plus(Duration.ofDays(2)));
        notices.noticeTrialsAt(Instant.now());
        notices.noticeTrialsAt(Instant.now());
        setTrialEnd(firm.workspaceId(), Instant.now().minus(Duration.ofHours(1)));
        notices.noticeTrialsAt(Instant.now());
        notices.noticeTrialsAt(Instant.now());

        List<String> subjects = email.subjectsFor(firm.owner());
        assertThat(subjects.subList(before, subjects.size())).satisfiesExactly(
                ending -> assertThat(ending).startsWith("Your Uncava trial ends on"),
                ended -> assertThat(ended).isEqualTo("Your Uncava trial has ended"));
    }

    private record Firm(UUID workspaceId, String owner, String adminToken) {
    }

    private Firm newFirm() throws Exception {
        String owner = "trial" + SEQUENCE.incrementAndGet() + "@" + domain;
        UUID workspaceId = UUID.fromString(createWorkspace(verifiedUser("Yara Haddad", owner), "Trial Firm"));
        return new Firm(workspaceId, owner, login(owner));
    }

    private JsonNode billingOf(Firm firm) throws Exception {
        return body(mvc.perform(get("/api/v1/billing").header("Authorization", "Bearer " + firm.adminToken()))
                .andExpect(status().isOk()).andReturn());
    }

    private void endTrial(UUID workspaceId) {
        setTrialEnd(workspaceId, Instant.now().minusSeconds(1));
    }

    private void setTrialEnd(UUID workspaceId, Instant endsAt) {
        db.update("UPDATE app_lm_workspace_subscription SET trial_ends_at = ? WHERE workspace_id = ?",
                Timestamp.from(endsAt), workspaceId);
    }

    private void deliver(PaymentEvent event) throws Exception {
        stripe.nextEvent(event);
        mvc.perform(post("/api/v1/billing/webhooks/stripe")
                        .header("Stripe-Signature", RecordingPaymentGateway.VALID_SIGNATURE)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    private static void assertRefusedAsEnded(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
                refused -> assertThat(refused.getCode()).isEqualTo(ErrorCode.TRIAL_ENDED));
    }
}
