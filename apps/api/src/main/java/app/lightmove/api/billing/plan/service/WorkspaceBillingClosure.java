package app.lightmove.api.billing.plan.service;

import app.lightmove.api.billing.payment.service.PaymentGateway;
import app.lightmove.api.billing.plan.model.WorkspaceSubscription;
import app.lightmove.api.billing.plan.repository.WorkspaceSubscriptionRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * A deleted workspace's Stripe subscription ends with the period already paid for, and a restored one's carries on.
 * Stripe is asked once the change commits; a refusal is logged for Uncava to settle by hand rather than undoing the
 * deletion, since access has already ended. An invoiced workspace's contract ends outside the app.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkspaceBillingClosure {

    private final WorkspaceSubscriptionRepository subscriptions;
    private final PaymentGateway gateway;

    @Transactional(propagation = Propagation.MANDATORY)
    public void onWorkspaceDeleted(UUID workspaceId) {
        cancelAtPeriodEndOnCommit(workspaceId, true);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void onWorkspaceRestored(UUID workspaceId) {
        cancelAtPeriodEndOnCommit(workspaceId, false);
    }

    private void cancelAtPeriodEndOnCommit(UUID workspaceId, boolean cancel) {
        subscriptions.findByWorkspaceId(workspaceId)
                .filter(WorkspaceSubscription::isBilledByStripe)
                .map(WorkspaceSubscription::getStripeSubscriptionId)
                .ifPresent(subscriptionId -> TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                try {
                                    gateway.cancelAtPeriodEnd(subscriptionId, cancel);
                                } catch (RuntimeException failed) {
                                    log.error("Stripe did not {} the subscription of workspace {}; settle it by hand",
                                            cancel ? "cancel" : "resume", workspaceId, failed);
                                }
                            }
                        }));
    }
}
