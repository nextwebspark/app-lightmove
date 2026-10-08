package app.lightmove.api.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingPaymentGateway;
import app.lightmove.api.billing.credit.service.MonthlyCreditReset;
import app.lightmove.api.billing.payment.model.PaymentEvent;
import app.lightmove.api.billing.payment.model.StripeSubscriptionState;
import app.lightmove.api.billing.payment.model.SubscriptionCheckout;
import app.lightmove.api.billing.plan.constant.SubscriptionStatus;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Paying through Stripe: Checkout and the portal for an admin, and the webhook that turns payments into credits. */
@IntegrationTest
class StripeBillingIntegrationTest extends BillingFlowSupport {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired private RecordingPaymentGateway stripe;
    @Autowired private MonthlyCreditReset reset;

    @Test
    @DisplayName("an admin's checkout is for the plan's seat price, one seat per staff member, on one customer")
    void subscriptionCheckoutPricesTheStaffSeats() throws Exception {
        Firm firm = newFirm();
        inviteAndAccept(firm.adminToken(), "Sara Al-Mansour", "sara" + SEQUENCE.incrementAndGet() + "@" + domain,
                "MEMBER");

        String first = body(checkout(firm.adminToken(), "PRO", "ANNUAL").andExpect(status().isOk()).andReturn())
                .get("url").asText();
        checkout(firm.adminToken(), "CORE", "MONTHLY").andExpect(status().isOk());

        assertThat(first).startsWith("https://checkout.stripe.test/");
        assertThat(stripe.customersCreatedFor(firm.workspaceId())).isEqualTo(1);
        SubscriptionCheckout asked = stripe.subscriptionCheckoutsOf(firm.workspaceId()).getFirst();
        assertThat(asked.priceId()).isEqualTo("price_test_pro_annual");
        assertThat(asked.seats()).isEqualTo(2);
        assertThat(asked.successUrl()).endsWith("/settings/billing?checkout=subscribed");
        assertThat(asked.customerId()).isEqualTo(stripe.subscriptionCheckoutsOf(firm.workspaceId()).get(1).customerId());
        assertThat(stripe.wasExpired(first)).isTrue();
    }

    @Test
    @DisplayName("a customer Stripe already bills is sent to the portal even before its webhook arrives")
    void aSubscriptionStripeHasNotReportedStillBlocksASecond() throws Exception {
        Firm firm = newFirm();
        stripe.customerPays(customerOf(firm));

        assertThat(codeOf(checkout(firm.adminToken(), "PRO", "MONTHLY")
                .andExpect(status().isConflict()).andReturn())).isEqualTo("SUBSCRIPTION_BILLED_BY_STRIPE");
    }

    @Test
    @DisplayName("an event for a price or pack this deployment does not sell is taken and ignored, never retried")
    void ignoresWhatItCannotPlace() throws Exception {
        Firm firm = newFirm();
        String customer = customerOf(firm);

        deliver(invoicePaid(customer, "sub_" + SEQUENCE.incrementAndGet(), "price_another_product", 2, periodStart()));
        deliver(new PaymentEvent.CreditsPaid("evt_" + UUID.randomUUID(), "checkout.session.completed", Instant.now(),
                customer, "pi_" + SEQUENCE.incrementAndGet(), "contact-9000", 15_000));

        assertThat(db.queryForObject("SELECT count(*) FROM app_lm_workspace_subscription WHERE workspace_id = ?",
                Long.class, firm.workspaceId())).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM app_lm_credit_grant WHERE workspace_id = ?",
                Long.class, firm.workspaceId())).isZero();
    }

    @Test
    @DisplayName("Enterprise and unknown packs cannot be bought, and a member cannot buy at all")
    void refusesWhatCannotBeBought() throws Exception {
        Firm firm = newFirm();
        String member = "member" + SEQUENCE.incrementAndGet() + "@" + domain;
        inviteAndAccept(firm.adminToken(), "Sara Al-Mansour", member, "MEMBER");

        assertThat(codeOf(checkout(firm.adminToken(), "ENTERPRISE", "MONTHLY")
                .andExpect(status().isBadRequest()).andReturn())).isEqualTo("BILLING_PLAN_UNKNOWN");
        assertThat(codeOf(buy(firm.adminToken(), "contact-9000")
                .andExpect(status().isBadRequest()).andReturn())).isEqualTo("BILLING_PACK_UNKNOWN");
        checkout(login(member), "PRO", "MONTHLY").andExpect(status().isForbidden());
        buy(login(member), "contact-100").andExpect(status().isForbidden());
        assertThat(stripe.customersCreatedFor(firm.workspaceId())).isZero();
    }

    @Test
    @DisplayName("a workspace already paying through Stripe changes plan in the portal, not in a second checkout")
    void aStripeSubscriberIsSentToThePortal() throws Exception {
        Firm firm = newFirm();
        String customer = customerOf(firm);
        deliver(invoicePaid(customer, "sub_" + SEQUENCE.incrementAndGet(), "price_test_core_monthly", 1, periodStart()));

        assertThat(codeOf(checkout(firm.adminToken(), "PRO", "MONTHLY")
                .andExpect(status().isConflict()).andReturn())).isEqualTo("SUBSCRIPTION_BILLED_BY_STRIPE");
        assertThat(body(mvc.perform(post("/api/v1/billing/portal").header("Authorization", "Bearer " + firm.adminToken()))
                .andExpect(status().isOk()).andReturn()).get("url").asText()).endsWith(customer);
    }

    @Test
    @DisplayName("a delivery whose signature does not verify is a 400 and writes nothing")
    void refusesAnUnsignedDelivery() throws Exception {
        String eventId = "evt_" + UUID.randomUUID();
        stripe.nextEvent(new PaymentEvent.CreditsPaid(eventId, "checkout.session.completed", Instant.now(), "cus_x",
                "pi_x", "contact-100", 15_000));

        mvc.perform(post("/api/v1/billing/webhooks/stripe").header("Stripe-Signature", "t=1,v1=forged")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        assertThat(db.queryForObject("SELECT count(*) FROM app_lm_billing_webhook_event WHERE event_id = ?",
                Long.class, eventId)).isZero();
    }

    @Test
    @DisplayName("a first paid invoice puts the workspace on Stripe and grants its month once, however often it arrives")
    void aPaidInvoiceGrantsTheMonthOnce() throws Exception {
        Firm firm = newFirm();
        String customer = customerOf(firm);
        Instant start = periodStart();
        PaymentEvent.InvoicePaid paid = invoicePaid(customer, "sub_" + SEQUENCE.incrementAndGet(),
                "price_test_pro_monthly", 3, start);

        deliver(paid);
        deliver(paid);
        reset.resetAt(Instant.now());

        Map<String, Object> subscription = subscriptionOf(firm.workspaceId());
        assertThat(subscription.get("plan_code")).isEqualTo("PRO");
        assertThat(subscription.get("seats")).isEqualTo(3);
        assertThat(subscription.get("status")).isEqualTo("ACTIVE");
        assertThat(((Timestamp) subscription.get("current_period_start")).toInstant()).isEqualTo(start);
        assertThat(db.queryForList("""
                SELECT amount FROM app_lm_credit_grant WHERE workspace_id = ? AND source = 'PLAN'""",
                Long.class, firm.workspaceId())).containsExactly(450L);
        assertThat(db.queryForObject("""
                SELECT external_ref FROM app_lm_credit_grant WHERE workspace_id = ? AND source = 'PLAN'""",
                String.class, firm.workspaceId())).isEqualTo(MonthlyCreditReset.grantKey(firm.workspaceId(), start));
        assertLedgerAddsUp(firm.workspaceId());
    }

    @Test
    @DisplayName("a paid pack is granted once at what it cost before VAT, kept for a year")
    void aPaidPackIsGrantedOnce() throws Exception {
        Firm firm = newFirm();
        String customer = customerOf(firm);
        String paymentIntent = "pi_" + SEQUENCE.incrementAndGet();

        deliver(new PaymentEvent.CreditsPaid("evt_" + UUID.randomUUID(), "checkout.session.completed",
                Instant.now(), customer, paymentIntent, "contact-100", 15_000));
        deliver(new PaymentEvent.CreditsPaid("evt_" + UUID.randomUUID(), "checkout.session.async_payment_succeeded",
                Instant.now(), customer, paymentIntent, "contact-100", 15_000));

        Map<String, Object> grant = db.queryForMap("""
                SELECT source, amount, fils_per_credit, expires_at FROM app_lm_credit_grant WHERE workspace_id = ?""",
                firm.workspaceId());
        assertThat(grant.get("source")).isEqualTo("PURCHASED");
        assertThat(grant.get("amount")).isEqualTo(100L);
        assertThat((BigDecimal) grant.get("fils_per_credit")).isEqualByComparingTo("150");
        assertThat(((Timestamp) grant.get("expires_at")).toInstant())
                .isAfter(Instant.now().atZone(ZoneOffset.UTC).plusMonths(12).minusDays(1).toInstant());
        assertThat(ledger.balanceOf(firm.workspaceId()).available()).isEqualTo(100);
    }

    @Test
    @DisplayName("a failed payment keeps the months coming through the grace, and stops them after it")
    void aFailedPaymentStopsTheMonthsAfterTheGrace() throws Exception {
        Firm firm = newFirm();
        String customer = customerOf(firm);
        String subscriptionId = "sub_" + SEQUENCE.incrementAndGet();
        Instant lastMonth = Instant.now().atZone(ZoneOffset.UTC).minusMonths(1).minusHours(1).toInstant()
                .truncatedTo(ChronoUnit.SECONDS);
        deliver(invoicePaid(customer, subscriptionId, "price_test_core_monthly", 2, lastMonth));
        deliver(new PaymentEvent.InvoicePaymentFailed("evt_" + UUID.randomUUID(), "invoice.payment_failed",
                Instant.now().minus(Duration.ofDays(8)), "in_" + UUID.randomUUID(), customer, subscriptionId));

        assertThat(subscriptionOf(firm.workspaceId()).get("status")).isEqualTo("PAST_DUE");
        reset.resetAt(Instant.now());
        assertThat(planGrantsOf(firm.workspaceId())).isEqualTo(1);

        db.update("UPDATE app_lm_workspace_subscription SET past_due_since = ? WHERE workspace_id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofDays(2))), firm.workspaceId());
        reset.resetAt(Instant.now());
        assertThat(planGrantsOf(firm.workspaceId())).isEqualTo(2);
    }

    @Test
    @DisplayName("an upgrade tops the month up to the new plan at once; a late, older event changes nothing")
    void anUpgradeTopsUpTheMonth() throws Exception {
        Firm firm = newFirm();
        String customer = customerOf(firm);
        String subscriptionId = "sub_" + SEQUENCE.incrementAndGet();
        Instant start = periodStart();
        deliver(invoicePaid(customer, subscriptionId, "price_test_core_monthly", 2, start));

        deliver(changed(customer, subscriptionId, "price_test_pro_monthly", 2, start, SubscriptionStatus.ACTIVE,
                Instant.now().plusSeconds(5)));
        deliver(changed(customer, subscriptionId, "price_test_core_monthly", 2, start, SubscriptionStatus.ACTIVE,
                Instant.now().minusSeconds(60)));

        assertThat(subscriptionOf(firm.workspaceId()).get("plan_code")).isEqualTo("PRO");
        assertThat(ledger.balanceOf(firm.workspaceId()).available()).isEqualTo(300);
        assertLedgerAddsUp(firm.workspaceId());

        deliver(changed(customer, subscriptionId, "price_test_pro_monthly", 2, start, SubscriptionStatus.CANCELLED,
                Instant.now().plusSeconds(10)));
        assertThat(subscriptionOf(firm.workspaceId()).get("status")).isEqualTo("CANCELLED");
        checkout(firm.adminToken(), "PRO", "MONTHLY").andExpect(status().isOk());
    }

    private record Firm(UUID workspaceId, String adminToken) {
    }

    private Firm newFirm() throws Exception {
        String owner = "payer" + SEQUENCE.incrementAndGet() + "@" + domain;
        UUID workspaceId = UUID.fromString(createWorkspace(verifiedUser("Yara Haddad", owner), "Paying Firm"));
        return new Firm(workspaceId, login(owner));
    }

    /** The customer a first checkout makes, which every later webhook names. */
    private String customerOf(Firm firm) throws Exception {
        buy(firm.adminToken(), "contact-100").andExpect(status().isOk());
        return stripe.creditsCheckoutsOf(firm.workspaceId()).getFirst().customerId();
    }

    private ResultActions checkout(String token, String plan, String interval) throws Exception {
        return mvc.perform(post("/api/v1/billing/checkout/subscription")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"planCode":"%s","interval":"%s"}""".formatted(plan, interval)));
    }

    private ResultActions buy(String token, String pack) throws Exception {
        return mvc.perform(post("/api/v1/billing/checkout/credits")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"pack":"%s"}""".formatted(pack)));
    }

    private void deliver(PaymentEvent event) throws Exception {
        stripe.nextEvent(event);
        mvc.perform(post("/api/v1/billing/webhooks/stripe")
                        .header("Stripe-Signature", RecordingPaymentGateway.VALID_SIGNATURE)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    private static Instant periodStart() {
        return Instant.now().minus(Duration.ofHours(1)).truncatedTo(ChronoUnit.SECONDS);
    }

    private static PaymentEvent.InvoicePaid invoicePaid(String customer, String subscriptionId, String priceId,
                                                        long seats, Instant start) {
        return new PaymentEvent.InvoicePaid("evt_" + UUID.randomUUID(), "invoice.paid", start.plusSeconds(1),
                "in_" + UUID.randomUUID(), customer, subscriptionId,
                new StripeSubscriptionState(customer, subscriptionId, SubscriptionStatus.ACTIVE, priceId, seats, start,
                        start.atZone(ZoneOffset.UTC).plusMonths(1).toInstant()),
                true);
    }

    private static PaymentEvent.SubscriptionChanged changed(String customer, String subscriptionId, String priceId,
                                                            long seats, Instant start, SubscriptionStatus status,
                                                            Instant at) {
        return new PaymentEvent.SubscriptionChanged("evt_" + UUID.randomUUID(), "customer.subscription.updated", at,
                new StripeSubscriptionState(customer, subscriptionId, status, priceId, seats, start,
                        start.atZone(ZoneOffset.UTC).plusMonths(1).toInstant()));
    }

    private Map<String, Object> subscriptionOf(UUID workspaceId) {
        return db.queryForMap("SELECT * FROM app_lm_workspace_subscription WHERE workspace_id = ?", workspaceId);
    }

    private long planGrantsOf(UUID workspaceId) {
        return db.queryForObject("SELECT count(*) FROM app_lm_credit_grant WHERE workspace_id = ? AND source = 'PLAN'",
                Long.class, workspaceId);
    }
}
