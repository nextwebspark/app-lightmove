package app.lightmove.api.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingPaymentGateway;
import app.lightmove.api.billing.payment.model.PaymentEvent;
import app.lightmove.api.billing.payment.model.StripeSubscriptionState;
import app.lightmove.api.billing.plan.constant.SubscriptionStatus;
import app.lightmove.api.billing.seat.service.StripeSeatSync;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

/** Staff seats: a Stripe subscription's quantity follows them, and an invoiced workspace is held to its agreed number. */
@IntegrationTest
@TestPropertySource(properties = "lightmove.billing.enforce=true")
class SeatBillingIntegrationTest extends BillingFlowSupport {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired private RecordingPaymentGateway stripe;
    @Autowired private StripeSeatSync sync;

    @AfterEach
    void stripeAcceptsSeatChangesAgain() {
        stripe.refuseSeatUpdates(false);
    }

    @Test
    @DisplayName("a staff member joining a Stripe subscription raises its quantity and brings their seat's share of the month once")
    void aJoiningStaffMemberRaisesTheQuantity() throws Exception {
        Firm firm = newFirm();
        String subscriptionId = subscribe(firm, Instant.now().minus(Duration.ofDays(15)));
        String member = email("sara");

        inviteAndAccept(firm.adminToken(), "Sara Al-Mansour", member, "MEMBER");
        awaitSeats(subscriptionId, 2);
        awaitSynced(firm.workspaceId());

        assertThat(seatGrantsOf(firm.workspaceId())).isEqualTo(1);
        assertThat(db.queryForObject("""
                SELECT amount FROM app_lm_credit_grant WHERE workspace_id = ? AND external_ref LIKE 'seat:%'""",
                Long.class, firm.workspaceId())).isBetween(65L, 80L);
        assertThat(auditsOf(firm.workspaceId(), "SEAT_ADDED")).isEqualTo(1);

        mvc.perform(delete("/api/v1/members/" + memberIdOf(firm.adminToken(), member))
                        .header("Authorization", "Bearer " + firm.adminToken()))
                .andExpect(status().isNoContent());
        awaitSeats(subscriptionId, 1);
        awaitSynced(firm.workspaceId());
        assertThat(auditsOf(firm.workspaceId(), "SEAT_REMOVED")).isEqualTo(1);

        invite(firm.adminToken(), member, "MEMBER").andExpect(status().isOk());
        accept(login(member), email.latestTokenFor(member)).andExpect(status().isOk());
        awaitSeats(subscriptionId, 2);
        awaitSynced(firm.workspaceId());
        sync.syncDueAt(Instant.now().plus(Duration.ofMinutes(5)));

        assertThat(seatGrantsOf(firm.workspaceId())).isEqualTo(1);
        assertLedgerAddsUp(firm.workspaceId());
    }

    @Test
    @DisplayName("a sync Stripe refuses keeps the membership and the seat's credits, and the job bills it once, granting nothing twice")
    void aRefusedSyncIsRetried() throws Exception {
        Firm firm = newFirm();
        String subscriptionId = subscribe(firm, Instant.now().minus(Duration.ofHours(1)));
        int refusedBefore = stripe.seatUpdatesRefused();
        stripe.refuseSeatUpdates(true);
        String member = email("omar");

        inviteAndAccept(firm.adminToken(), "Omar Khalil", member, "MEMBER");
        Awaitility.await().atMost(Duration.ofSeconds(10))
                .until(() -> stripe.seatUpdatesRefused() > refusedBefore);

        assertThat(memberIdOf(firm.adminToken(), member)).isNotBlank();
        assertThat(stripe.seatsOf(subscriptionId)).isEqualTo(1);
        assertThat(seatSyncDueAt(firm.workspaceId())).isNotNull();
        assertThat(seatGrantsOf(firm.workspaceId())).isEqualTo(1);
        assertThat(auditsOf(firm.workspaceId(), "SEAT_ADDED")).isZero();

        stripe.refuseSeatUpdates(false);
        sync.syncDueAt(Instant.now().plus(Duration.ofMinutes(2)));

        assertThat(stripe.seatsOf(subscriptionId)).isEqualTo(2);
        assertThat(seatSyncDueAt(firm.workspaceId())).isNull();
        assertThat(seatGrantsOf(firm.workspaceId())).isEqualTo(1);
        assertThat(auditsOf(firm.workspaceId(), "SEAT_ADDED")).isEqualTo(1);
        assertLedgerAddsUp(firm.workspaceId());
    }

    @Test
    @DisplayName("an invoiced workspace at its agreed seats refuses more staff, pending invitations counted, but never a client")
    void anInvoicedWorkspaceIsHeldToItsSeats() throws Exception {
        Firm firm = newFirm();
        db.update("""
                INSERT INTO app_lm_workspace_subscription (workspace_id, plan_code, billing_interval, seats, status)
                VALUES (?, 'CORE', 'MONTHLY', 2, 'INVOICED')""", firm.workspaceId());
        String colleague = email("layla");

        invite(firm.adminToken(), colleague, "MEMBER").andExpect(status().isOk());
        ResultActions refused = invite(firm.adminToken(), email("karim"), "MEMBER").andExpect(status().isConflict());

        assertThat(codeOf(refused.andReturn())).isEqualTo("SEAT_LIMIT_REACHED");
        assertThat(body(refused.andReturn()).get("seats").asInt()).isEqualTo(2);
        String invitation = email.latestTokenFor(colleague);
        verifiedUser("Layla Haddad", colleague);
        accept(login(colleague), invitation).andExpect(status().isOk());

        String representative = email("rep");
        clientRepresentative(firm.adminToken(), "Huda Saleh", representative);
        String representativeMemberId = db.queryForObject("""
                SELECT m.id::text FROM app_lm_workspace_member m JOIN app_lm_user u ON u.id = m.user_id
                WHERE m.workspace_id = ? AND u.email = ?""", String.class, firm.workspaceId(), representative);
        assertThat(codeOf(promote(firm.adminToken(), representativeMemberId)
                .andExpect(status().isConflict()).andReturn())).isEqualTo("SEAT_LIMIT_REACHED");

        db.update("UPDATE app_lm_workspace_subscription SET seats = 3 WHERE workspace_id = ?", firm.workspaceId());
        promote(firm.adminToken(), representativeMemberId).andExpect(status().isOk());
    }

    private record Firm(UUID workspaceId, String adminToken) {
    }

    private Firm newFirm() throws Exception {
        String owner = email("owner");
        UUID workspaceId = UUID.fromString(createWorkspace(verifiedUser("Yara Haddad", owner), "Seated Firm"));
        return new Firm(workspaceId, login(owner));
    }

    private String email(String name) {
        return name + "-seat" + SEQUENCE.incrementAndGet() + "@" + domain;
    }

    /** A one-seat Pro subscription Stripe bills, its period opened at {@code start}. */
    private String subscribe(Firm firm, Instant start) throws Exception {
        mvc.perform(post("/api/v1/billing/checkout/credits")
                        .header("Authorization", "Bearer " + firm.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pack":"contact-100"}"""))
                .andExpect(status().isOk());
        String customer = stripe.creditsCheckoutsOf(firm.workspaceId()).getFirst().customerId();
        String subscriptionId = "sub_seat_" + SEQUENCE.incrementAndGet();
        Instant periodStart = start.truncatedTo(ChronoUnit.SECONDS);
        stripe.nextEvent(new PaymentEvent.InvoicePaid("evt_" + UUID.randomUUID(), "invoice.paid",
                periodStart.plusSeconds(1), "in_" + UUID.randomUUID(), customer, subscriptionId,
                new StripeSubscriptionState(customer, subscriptionId, SubscriptionStatus.ACTIVE,
                        "price_test_pro_monthly", 1, periodStart,
                        periodStart.atZone(ZoneOffset.UTC).plusMonths(1).toInstant()),
                true));
        mvc.perform(post("/api/v1/billing/webhooks/stripe")
                        .header("Stripe-Signature", RecordingPaymentGateway.VALID_SIGNATURE)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        stripe.subscriptionHolds(subscriptionId, 1);
        return subscriptionId;
    }

    private ResultActions invite(String adminToken, String invitee, String role) throws Exception {
        return mvc.perform(post("/api/v1/invitations")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        [{"email":"%s","role":"%s"}]""".formatted(invitee, role)));
    }

    private ResultActions accept(String inviteeToken, String invitationToken) throws Exception {
        return mvc.perform(post("/api/v1/onboarding/invitations/accept")
                .header("Authorization", "Bearer " + inviteeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token":"%s"}""".formatted(invitationToken)));
    }

    private ResultActions promote(String adminToken, String memberId) throws Exception {
        return mvc.perform(patch("/api/v1/members/" + memberId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"roles":["MEMBER"]}"""));
    }

    private void awaitSeats(String subscriptionId, long seats) {
        Awaitility.await().atMost(Duration.ofSeconds(10))
                .until(() -> Long.valueOf(seats).equals(stripe.seatsOf(subscriptionId)));
    }

    private void awaitSynced(UUID workspaceId) {
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> seatSyncDueAt(workspaceId) == null);
    }

    private Timestamp seatSyncDueAt(UUID workspaceId) {
        return db.queryForObject("SELECT seat_sync_due_at FROM app_lm_workspace_subscription WHERE workspace_id = ?",
                Timestamp.class, workspaceId);
    }

    private long seatGrantsOf(UUID workspaceId) {
        return db.queryForObject("""
                SELECT count(*) FROM app_lm_credit_grant WHERE workspace_id = ? AND external_ref LIKE 'seat:%'""",
                Long.class, workspaceId);
    }

    private long auditsOf(UUID workspaceId, String eventType) {
        return db.queryForObject("SELECT count(*) FROM app_lm_audit_event WHERE workspace_id = ? AND event_type = ?",
                Long.class, workspaceId, eventType);
    }
}
