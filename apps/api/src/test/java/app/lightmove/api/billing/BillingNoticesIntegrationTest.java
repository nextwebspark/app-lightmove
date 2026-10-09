package app.lightmove.api.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingPaymentGateway;
import app.lightmove.api.billing.credit.constant.CreditGrantSource;
import app.lightmove.api.billing.notice.service.BillingNotices;
import app.lightmove.api.billing.payment.model.PaymentEvent;
import app.lightmove.api.billing.payment.model.StripeSubscriptionState;
import app.lightmove.api.billing.plan.constant.SubscriptionStatus;
import app.lightmove.api.core.email.model.EmailMessage;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** What billing emails a workspace's billing managers, and that each reaches them once. */
@IntegrationTest
class BillingNoticesIntegrationTest extends BillingFlowSupport {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired private RecordingPaymentGateway stripe;
    @Autowired private BillingNotices notices;

    @Test
    @DisplayName("concurrent spends crossing 80% email each admin once, an invoiced one to contact Uncava, a member never")
    void crossingEightyEmailsEachAdminOnce() throws Exception {
        Firm firm = newFirm();
        String member = address("member");
        inviteAndAccept(firm.adminToken(), "Omar Khalil", member, "MEMBER");
        invoiced(firm.workspaceId());
        grant(firm.workspaceId(), CreditGrantSource.PLAN, 20, Duration.ofDays(20));
        for (int i = 1; i <= 3; i++) {
            ledger.charge(phoneFound(firm.workspaceId(), "phone-" + i));
        }

        AtomicInteger find = new AtomicInteger();
        bothAtOnce(() -> ledger.charge(emailFound(firm.workspaceId(), "email-" + find.incrementAndGet())));

        assertThat(noticesTo(firm)).containsExactly("80% of this month's contact credits used");
        assertThat(email.subjectsFor(member)).noneMatch(subject -> subject.contains("contact credits"));
        assertThat(lastTo(firm.owner()).textBody())
                .contains("4 credits left")
                .contains("Contact Uncava")
                .contains("mailto:billing@uncava.com")
                .doesNotContain("Buy more credits");
    }

    @Test
    @DisplayName("a new billing month claims the thresholds afresh")
    void aNewMonthResetsTheThresholds() throws Exception {
        Firm firm = newFirm();
        grant(firm.workspaceId(), CreditGrantSource.PLAN, 20, Duration.ofDays(20));
        for (int i = 1; i <= 3; i++) {
            ledger.charge(phoneFound(firm.workspaceId(), "phone-" + i));
        }
        ledger.charge(emailFound(firm.workspaceId(), "email-1"));
        assertThat(noticesTo(firm)).containsExactly("80% of this month's contact credits used");

        db.update("""
                UPDATE app_lm_credit_threshold_crossing SET month_start = month_start - interval '1 month'
                WHERE workspace_id = ?""", firm.workspaceId());
        ledger.charge(emailFound(firm.workspaceId(), "email-2"));

        assertThat(noticesTo(firm)).containsExactly("80% of this month's contact credits used",
                "80% of this month's contact credits used");
    }

    @Test
    @DisplayName("a workspace paying by card is sent to buy more credits")
    void aCardCustomerIsOfferedMoreCredits() throws Exception {
        Firm firm = newFirm();
        subscribeOnStripe(firm);

        for (int i = 1; i <= 8; i++) {
            ledger.charge(phoneFound(firm.workspaceId(), "phone-" + i));
        }

        assertThat(noticesTo(firm)).containsExactly("80% of this month's contact credits used");
        assertThat(lastTo(firm.owner()).textBody())
                .contains("Buy more credits")
                .contains("/settings/billing")
                .doesNotContain("Contact Uncava");
    }

    @Test
    @DisplayName("a failed invoice emails once, replayed or retried by Stripe; the next failed invoice emails again")
    void aFailedInvoiceEmailsOnce() throws Exception {
        Firm firm = newFirm();
        Subscribed subscribed = subscribeOnStripe(firm);
        String invoice = "in_" + UUID.randomUUID();
        PaymentEvent.InvoicePaymentFailed failed = paymentFailed(subscribed, invoice);

        deliver(failed);
        deliver(failed);
        deliver(paymentFailed(subscribed, invoice));

        assertThat(noticesTo(firm)).containsExactly("Your Uncava payment failed");
        assertThat(lastTo(firm.owner()).textBody()).contains("7 days").contains("/settings/billing");

        deliver(paymentFailed(subscribed, "in_" + UUID.randomUUID()));
        assertThat(noticesTo(firm)).containsExactly("Your Uncava payment failed", "Your Uncava payment failed");
    }

    @Test
    @DisplayName("bought credits lapsing within a week are warned of once; spent or later ones are not")
    void expiringBoughtCreditsAreWarnedOfOnce() throws Exception {
        Firm firm = newFirm();
        grant(firm.workspaceId(), CreditGrantSource.PURCHASED, 1, Duration.ofDays(3));
        grant(firm.workspaceId(), CreditGrantSource.PURCHASED, 40, Duration.ofDays(5));
        grant(firm.workspaceId(), CreditGrantSource.PURCHASED, 100, Duration.ofDays(30));
        ledger.charge(emailFound(firm.workspaceId(), "email-1"));
        grant(firm.workspaceId(), CreditGrantSource.MANUAL, 25, Duration.ofDays(3));

        notices.warnOfExpiringCreditsAt(Instant.now());
        notices.warnOfExpiringCreditsAt(Instant.now());

        assertThat(noticesTo(firm)).singleElement().asString().startsWith("Bought contact credits expire on");
        assertThat(lastTo(firm.owner()).textBody()).contains("40 credits");
    }

    /** @param emailsBefore the emails the owner already had, their verification among them */
    private record Firm(UUID workspaceId, String owner, String adminToken, int emailsBefore) {
    }

    private record Subscribed(String customer, String subscriptionId) {
    }

    private Firm newFirm() throws Exception {
        String owner = address("owner");
        UUID workspaceId = UUID.fromString(createWorkspace(verifiedUser("Yara Haddad", owner), "Noticed Firm"));
        return new Firm(workspaceId, owner, login(owner), email.subjectsFor(owner).size());
    }

    private String address(String name) {
        return name + "-notice" + SEQUENCE.incrementAndGet() + "@" + domain;
    }

    private void invoiced(UUID workspaceId) {
        db.update("""
                INSERT INTO app_lm_workspace_subscription (workspace_id, plan_code, billing_interval, seats, status)
                VALUES (?, 'CORE', 'MONTHLY', 2, 'INVOICED')""", workspaceId);
    }

    /** A one-seat Core subscription Stripe bills, granted its 50 credits for the month. */
    private Subscribed subscribeOnStripe(Firm firm) throws Exception {
        mvc.perform(post("/api/v1/billing/checkout/credits")
                        .header("Authorization", "Bearer " + firm.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pack":"contact-100"}"""))
                .andExpect(status().isOk());
        String customer = stripe.creditsCheckoutsOf(firm.workspaceId()).getFirst().customerId();
        String subscriptionId = "sub_notice_" + SEQUENCE.incrementAndGet();
        Instant start = Instant.now().minus(Duration.ofHours(1)).truncatedTo(ChronoUnit.SECONDS);
        deliver(new PaymentEvent.InvoicePaid("evt_" + UUID.randomUUID(), "invoice.paid", start.plusSeconds(1),
                "in_" + UUID.randomUUID(), customer, subscriptionId,
                new StripeSubscriptionState(customer, subscriptionId, SubscriptionStatus.ACTIVE,
                        "price_test_core_monthly", 1, start, start.atZone(ZoneOffset.UTC).plusMonths(1).toInstant()),
                true));
        return new Subscribed(customer, subscriptionId);
    }

    private static PaymentEvent.InvoicePaymentFailed paymentFailed(Subscribed subscribed, String invoiceId) {
        return new PaymentEvent.InvoicePaymentFailed("evt_" + UUID.randomUUID(), "invoice.payment_failed",
                Instant.now(), invoiceId, subscribed.customer(), subscribed.subscriptionId());
    }

    private void deliver(PaymentEvent event) throws Exception {
        stripe.nextEvent(event);
        mvc.perform(post("/api/v1/billing/webhooks/stripe")
                        .header("Stripe-Signature", RecordingPaymentGateway.VALID_SIGNATURE)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    private List<String> noticesTo(Firm firm) {
        List<String> subjects = email.subjectsFor(firm.owner());
        return subjects.subList(firm.emailsBefore(), subjects.size());
    }

    private EmailMessage lastTo(String recipient) {
        return email.sent().reversed().stream()
                .filter(message -> recipient.equalsIgnoreCase(message.to()))
                .findFirst().orElseThrow();
    }

    private static void bothAtOnce(Runnable work) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<?>> both = List.of(pool.submit(() -> await(start, work)), pool.submit(() -> await(start, work)));
            start.countDown();
            for (Future<?> one : both) {
                one.get();
            }
        }
    }

    private static void await(CountDownLatch start, Runnable work) {
        try {
            start.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return;
        }
        work.run();
    }
}
