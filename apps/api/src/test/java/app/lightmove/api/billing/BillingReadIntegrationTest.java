package app.lightmove.api.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.billing.credit.constant.CreditAction;
import app.lightmove.api.billing.credit.constant.CreditGrantSource;
import app.lightmove.api.billing.credit.model.CreditCharge;
import app.lightmove.api.billing.credit.service.GrandfatherCredits;
import app.lightmove.api.core.config.LightMoveProperties;
import java.sql.Timestamp;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/** Settings → Billing's two reads, as any staff member reads them with enforcement on, and the grandfather grant. */
@IntegrationTest
@TestPropertySource(properties = "lightmove.billing.enforce=true")
class BillingReadIntegrationTest extends BillingFlowSupport {

    @Autowired private GrandfatherCredits grandfather;
    @Autowired private LightMoveProperties properties;

    @Test
    @DisplayName("the billing read carries the plan, its seat price, the credits by where they came from, and the prices")
    void readsThePlanAndTheCredits() throws Exception {
        String owner = "owner@" + domain;
        UUID workspace = UUID.fromString(createWorkspace(verifiedUser("Yara Haddad", owner), "Read Firm"));
        Instant periodStart = Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(Duration.ofDays(3));
        subscribe(workspace, "PRO", 2, periodStart);
        grant(workspace, CreditGrantSource.PLAN, 300, Duration.ofDays(20));
        grant(workspace, CreditGrantSource.PURCHASED, 20, Duration.ofDays(300));
        grant(workspace, CreditGrantSource.MANUAL, 5, null);

        JsonNode billing = body(read(login(owner), "/api/v1/billing").andExpect(status().isOk()).andReturn());

        assertThat(billing.at("/plan/code").asText()).isEqualTo("PRO");
        assertThat(billing.at("/plan/name").asText()).isEqualTo("Pro");
        assertThat(billing.get("interval").asText()).isEqualTo("MONTHLY");
        assertThat(billing.get("seats").asInt()).isEqualTo(2);
        assertThat(billing.get("seatPriceFils").asLong()).isEqualTo(49_900);
        assertThat(billing.get("status").asText()).isEqualTo("INVOICED");
        assertThat(billing.at("/credits/monthly").asLong()).isEqualTo(300);
        assertThat(billing.at("/credits/left").asLong()).isEqualTo(325);
        assertThat(billing.at("/credits/bought").asLong()).isEqualTo(20);
        assertThat(billing.at("/credits/given").asLong()).isEqualTo(5);
        assertThat(billing.at("/credits/level").asText()).isEqualTo("OK");
        assertThat(Instant.parse(billing.at("/credits/resetsAt").asText()))
                .isEqualTo(periodStart.atZone(ZoneOffset.UTC).plusMonths(1).toInstant());
        assertThat(billing.at("/prices/email").asLong()).isEqualTo(1);
        assertThat(billing.at("/prices/phone").asLong()).isEqualTo(5);
        assertThat(billing.at("/paymentMethod/kind").asText()).isEqualTo("INVOICED");
        assertThat(billing.get("stripeOffered").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("the billing read offers the plans per seat in the intervals Stripe sells them, and the packs by size")
    void offersThePlansAndThePacks() throws Exception {
        String owner = "catalogue@" + domain;
        createWorkspace(verifiedUser("Yara Haddad", owner), "Catalogue Firm");

        JsonNode billing = body(read(login(owner), "/api/v1/billing").andExpect(status().isOk()).andReturn());

        assertThat(billing.get("plans")).extracting(plan -> plan.get("code").asText())
                .containsExactly("CORE", "PRO", "ENTERPRISE");
        JsonNode pro = billing.get("plans").get(1);
        assertThat(pro.get("seatPriceMonthlyFils").asLong()).isEqualTo(49_900);
        assertThat(pro.get("seatPriceAnnualFils").asLong()).isEqualTo(39_900);
        assertThat(pro.get("contactCreditsPerSeat").asInt()).isEqualTo(150);
        assertThat(pro.get("checkoutIntervals")).extracting(JsonNode::asText).containsExactly("MONTHLY", "ANNUAL");
        JsonNode enterprise = billing.get("plans").get(2);
        assertThat(enterprise.get("custom").asBoolean()).isTrue();
        assertThat(enterprise.get("seatPriceMonthlyFils").isNull()).isTrue();
        assertThat(enterprise.get("checkoutIntervals")).isEmpty();
        assertThat(billing.get("packs")).extracting(pack -> pack.get("code").asText() + ":" + pack.get("credits")
                + ":" + pack.get("priceFils")).containsExactly("contact-100:100:15000", "contact-500:500:65000");
    }

    @Test
    @DisplayName("the level moves OK, 80%, 90%, then used up as the month's credits are spent")
    void theLevelFollowsTheSpend() throws Exception {
        String owner = "owner@" + domain;
        UUID workspace = UUID.fromString(createWorkspace(verifiedUser("Yara Haddad", owner), "Level Firm"));
        String token = login(owner);
        grant(workspace, CreditGrantSource.PLAN, 10, Duration.ofDays(20));

        for (int i = 1; i <= 7; i++) {
            ledger.charge(emailFound(workspace, "email-" + i));
        }
        assertThat(levelOf(token)).isEqualTo("OK");
        ledger.charge(emailFound(workspace, "email-8"));
        assertThat(levelOf(token)).isEqualTo("EIGHTY");
        ledger.charge(emailFound(workspace, "email-9"));
        assertThat(levelOf(token)).isEqualTo("NINETY");
        ledger.charge(emailFound(workspace, "email-10"));
        assertThat(levelOf(token)).isEqualTo("OUT");
    }

    @Test
    @DisplayName("usage counts this month's finds and credits per member, most first, leaving out what was given back")
    void usageIsPerMemberThisMonth() throws Exception {
        String owner = "owner@" + domain;
        String colleague = "sara@" + domain;
        UUID workspace = UUID.fromString(createWorkspace(verifiedUser("Yara Haddad", owner), "Usage Firm"));
        String ownerToken = login(owner);
        inviteAndAccept(ownerToken, "Sara Al-Mansour", colleague, "MEMBER");
        UUID yara = userIdOf(owner);
        UUID sara = userIdOf(colleague);
        grant(workspace, CreditGrantSource.MANUAL, 100, null);

        ledger.charge(new CreditCharge(workspace, CreditAction.EMAIL_FOUND, "yara-email-1", yara, null, null));
        ledger.charge(new CreditCharge(workspace, CreditAction.EMAIL_FOUND, "yara-email-2", yara, null, null));
        ledger.charge(new CreditCharge(workspace, CreditAction.PHONE_FOUND, "yara-phone-1", yara, null, null));
        ledger.charge(new CreditCharge(workspace, CreditAction.EMAIL_FOUND, "sara-email-1", sara, null, null));
        UUID released = ledger.hold(new CreditCharge(workspace, CreditAction.PHONE_FOUND, "sara-miss", sara, null,
                null)).holdId();
        ledger.release(workspace, released);
        ledger.charge(new CreditCharge(workspace, CreditAction.PHONE_FOUND, "sara-last-month", sara, null, null));
        db.update("""
                UPDATE app_lm_credit_hold SET created_at = now() - interval '40 days'
                WHERE workspace_id = ? AND idempotency_key = 'sara-last-month'""", workspace);

        JsonNode usage = body(read(login(colleague), "/api/v1/billing/usage").andExpect(status().isOk()).andReturn());

        assertThat(usage.get("members")).hasSize(2);
        assertThat(usage.at("/members/0/userId").asText()).isEqualTo(yara.toString());
        assertThat(usage.at("/members/0/name").asText()).isEqualTo("Yara Haddad");
        assertThat(usage.at("/members/0/emailsFound").asLong()).isEqualTo(2);
        assertThat(usage.at("/members/0/phonesFound").asLong()).isEqualTo(1);
        assertThat(usage.at("/members/0/creditsSpent").asLong()).isEqualTo(7);
        assertThat(usage.at("/members/1/userId").asText()).isEqualTo(sara.toString());
        assertThat(usage.at("/members/1/emailsFound").asLong()).isEqualTo(1);
        assertThat(usage.at("/members/1/phonesFound").asLong()).isZero();
        assertThat(usage.at("/members/1/creditsSpent").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("a pure client is told neither read exists")
    void aPureClientReadsNothing() throws Exception {
        String owner = "owner@" + domain;
        createWorkspace(verifiedUser("Yara Haddad", owner), "Client Firm");
        String client = clientRepresentative(login(owner), "Rana Client", "rana@client-" + domain);

        read(client, "/api/v1/billing").andExpect(status().isNotFound());
        read(client, "/api/v1/billing/usage").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("the grandfather grant reaches every workspace once however many boots run it, and none founded after")
    void grandfatherGrantsOnce() throws Exception {
        UUID existing = newWorkspace();
        db.update("DELETE FROM app_lm_billing_job_run WHERE job = 'grandfather-credits'");

        bothAtOnce(() -> grandfather.grantAt(Instant.now()));
        assertThat(grandfather.grantAt(Instant.now())).isZero();
        UUID foundedAfter = newWorkspace();
        assertThat(grandfather.grantAt(Instant.now())).isZero();

        assertThat(db.queryForList("""
                SELECT amount FROM app_lm_credit_grant WHERE workspace_id = ? AND source = 'PROMO' AND external_ref = ?""",
                Long.class, existing, GrandfatherCredits.grantKey(existing)))
                .containsExactly(properties.billing().grandfather().credits());
        assertThat(ledger.balanceOf(foundedAfter).available()).isZero();
        assertLedgerAddsUp(existing);
    }

    private ResultActions read(String token, String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", "Bearer " + token));
    }

    private String levelOf(String token) throws Exception {
        return body(read(token, "/api/v1/billing").andExpect(status().isOk()).andReturn()).at("/credits/level").asText();
    }

    private UUID userIdOf(String emailAddress) {
        return db.queryForObject("SELECT id FROM app_lm_user WHERE email = ?", UUID.class, emailAddress);
    }

    private void subscribe(UUID workspaceId, String plan, int seats, Instant periodStart) {
        db.update("""
                INSERT INTO app_lm_workspace_subscription (workspace_id, plan_code, billing_interval, seats, status,
                                                           current_period_start, current_period_end)
                VALUES (?, ?, 'MONTHLY', ?, 'INVOICED', ?, ?)""",
                workspaceId, plan, seats, Timestamp.from(periodStart),
                Timestamp.from(periodStart.atZone(ZoneOffset.UTC).plusMonths(1).toInstant()));
    }

    private static void bothAtOnce(Runnable work) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Runnable gated = () -> {
                try {
                    start.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
                work.run();
            };
            List<Future<?>> runs = List.of(pool.submit(gated), pool.submit(gated));
            start.countDown();
            for (Future<?> run : runs) {
                run.get();
            }
        }
    }
}
