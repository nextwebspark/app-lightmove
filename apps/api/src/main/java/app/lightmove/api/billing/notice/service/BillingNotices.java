package app.lightmove.api.billing.notice.service;

import app.lightmove.api.billing.credit.constant.ContactCreditLevel;
import app.lightmove.api.billing.credit.model.ContactCreditThresholdCrossed;
import app.lightmove.api.billing.credit.model.CreditGrant;
import app.lightmove.api.billing.credit.repository.CreditGrantRepository;
import app.lightmove.api.billing.notice.constant.BillingNoticeKind;
import app.lightmove.api.billing.payment.model.SubscriptionPaymentFailed;
import app.lightmove.api.billing.payment.service.PaymentGateway;
import app.lightmove.api.billing.plan.model.BillingManager;
import app.lightmove.api.billing.plan.model.WorkspaceSubscription;
import app.lightmove.api.billing.plan.repository.WorkspaceSubscriptionRepository;
import app.lightmove.api.billing.plan.service.BillingWorkspaces;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.email.model.EmailMessage;
import app.lightmove.api.core.email.render.EmailAction;
import app.lightmove.api.core.email.service.EmailSender;
import app.lightmove.api.core.email.service.EmailTemplates;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Emails a workspace's billing managers — never its other members — as its contact credits run low, when a payment
 * fails, and a week before bought credits lapse. Each is claimed once before any email goes: the thresholds by the
 * ledger (V124), the rest here (V127), so two deliveries or two instances send once.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillingNotices {

    static final Duration EXPIRY_WARNING = Duration.ofDays(7);

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private final BillingWorkspaces workspaces;
    private final WorkspaceSubscriptionRepository subscriptions;
    private final CreditGrantRepository grants;
    private final PaymentGateway gateway;
    private final EmailTemplates templates;
    private final EmailSender emailSender;
    private final BillingNoticeClaims claims;
    private final LightMoveProperties properties;
    private final Clock clock;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onThresholdCrossed(ContactCreditThresholdCrossed crossed) {
        String resetsOn = DATE.format(crossed.resetsAt());
        EmailAction moreCredits = moreCreditsFor(crossed.workspaceId());
        String workspaceName = workspaces.nameOf(crossed.workspaceId());
        sendToManagers(crossed.workspaceId(), manager -> crossed.level() == ContactCreditLevel.OUT
                ? templates.buildContactCreditsUsedUpEmail(manager.email(), manager.fullName(), workspaceName,
                        resetsOn, moreCredits)
                : templates.buildContactCreditsLowEmail(manager.email(), manager.fullName(), workspaceName,
                        crossed.level().usedPercent(), crossed.creditsLeft(), resetsOn, moreCredits));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentFailed(SubscriptionPaymentFailed failed) {
        if (!claims.claim(BillingNoticeKind.PAYMENT_FAILED, failed.invoiceId(), failed.workspaceId())) {
            return;
        }
        String workspaceName = workspaces.nameOf(failed.workspaceId());
        long graceDays = properties.billing().pastDueGrace().toDays();
        sendToManagers(failed.workspaceId(), manager -> templates.buildPaymentFailedEmail(manager.email(),
                manager.fullName(), workspaceName, graceDays, billingLink()));
    }

    @Scheduled(cron = "${lightmove.billing.jobs.purchased-credit-expiry}", zone = "UTC")
    public void scheduledExpiryWarnings() {
        warnOfExpiringCreditsAt(clock.instant());
    }

    /** One pass; a grant that fails is logged and the ones after it still warned. */
    public int warnOfExpiringCreditsAt(Instant now) {
        int warned = 0;
        for (CreditGrant grant : grants.findPurchasedExpiring(now, now.plus(EXPIRY_WARNING))) {
            try {
                warned += warnOfExpiring(grant) ? 1 : 0;
            } catch (RuntimeException failure) {
                log.error("The expiry warning for credit grant {} failed", grant.getId(), failure);
            }
        }
        return warned;
    }

    private boolean warnOfExpiring(CreditGrant grant) {
        if (!claims.claim(BillingNoticeKind.PURCHASED_CREDITS_EXPIRING, grant.getId().toString(),
                grant.getWorkspaceId())) {
            return false;
        }
        String workspaceName = workspaces.nameOf(grant.getWorkspaceId());
        String expiresOn = DATE.format(grant.getExpiresAt());
        sendToManagers(grant.getWorkspaceId(), manager -> templates.buildPurchasedCreditsExpiringEmail(
                manager.email(), manager.fullName(), workspaceName, grant.getRemaining(), expiresOn, billingLink()));
        return true;
    }

    /** A card customer buys more in Settings → Billing; an invoiced one asks Uncava, as the page says. */
    private EmailAction moreCreditsFor(UUID workspaceId) {
        boolean buysByCard = gateway.isOffered() && subscriptions.findByWorkspaceId(workspaceId)
                .map(WorkspaceSubscription::isBilledByStripe).orElse(false);
        return buysByCard
                ? new EmailAction("Buy more credits", billingLink())
                : new EmailAction("Contact Uncava",
                        "mailto:" + properties.billing().contactEmail() + "?subject=More%20contact%20credits");
    }

    /** One manager's failed send never costs the others theirs: the claim has committed, so nothing retries. */
    private void sendToManagers(UUID workspaceId, Function<BillingManager, EmailMessage> email) {
        for (BillingManager manager : workspaces.managersOf(workspaceId)) {
            try {
                emailSender.send(email.apply(manager));
            } catch (RuntimeException failure) {
                log.error("A billing email to a manager of workspace {} could not be sent", workspaceId, failure);
            }
        }
    }

    private String billingLink() {
        return properties.web().baseUrl() + "/settings/billing";
    }
}
