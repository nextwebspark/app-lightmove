package app.lightmove.api.billing.plan.service;

import app.lightmove.api.billing.plan.dto.InvoicedSubscriptionRequest;
import app.lightmove.api.billing.plan.dto.SubscriptionResponse;
import app.lightmove.api.billing.plan.model.BillingPlan;
import app.lightmove.api.billing.plan.model.WorkspaceSubscription;
import app.lightmove.api.billing.plan.repository.BillingPlanRepository;
import app.lightmove.api.billing.plan.repository.WorkspaceSubscriptionRepository;
import app.lightmove.api.core.audit.constant.PlatformEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The plan and seats of a workspace billed outside Stripe; a Stripe-billed one is its webhooks' to change. */
@Service
@RequiredArgsConstructor
public class InvoicedSubscriptionService {

    private final WorkspaceSubscriptionRepository subscriptions;
    private final BillingPlanRepository plans;
    private final BillingWorkspaces workspaces;
    private final AuditService audit;

    @Transactional
    public SubscriptionResponse set(UUID actorId, UUID workspaceId, InvoicedSubscriptionRequest request,
                                    HttpServletRequest httpRequest) {
        workspaces.requireExists(workspaceId);
        BillingPlan plan = plans.findById(request.plan())
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR,
                        "plan " + request.plan() + " is missing from app_lm_billing_plan"));
        if (plan.isCustom() && request.contactCreditPool() == null) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "contactCreditPool",
                    "Enter the contact credits agreed for this workspace");
        }
        if (!plan.isCustom() && request.contactCreditPool() != null) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "contactCreditPool",
                    "Only an Enterprise plan has an agreed pool; this plan's credits come from its seats");
        }
        if (request.currentPeriodStart() != null && request.currentPeriodEnd() != null
                && !request.currentPeriodEnd().isAfter(request.currentPeriodStart())) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "currentPeriodEnd",
                    "The period must end after it starts");
        }

        WorkspaceSubscription subscription = subscriptions.findByWorkspaceId(workspaceId)
                .orElseGet(() -> WorkspaceSubscription.invoiced(workspaceId));
        if (subscription.isBilledByStripe()) {
            throw ApiException.of(ErrorCode.SUBSCRIPTION_BILLED_BY_STRIPE);
        }
        subscription.invoice(plan, request.billingInterval(), request.seats(), request.contactCreditPool(),
                request.currentPeriodStart(), request.currentPeriodEnd());
        subscriptions.saveAndFlush(subscription);

        audit.event(PlatformEventType.INVOICED_SUBSCRIPTION_SET).actor(actorId).workspace(workspaceId)
                .target("workspace", workspaceId)
                .detail("plan", plan.getCode().name())
                .detail("billingInterval", request.billingInterval().name())
                .detail("seats", request.seats())
                .from(httpRequest)
                .record();
        return responseOf(subscription, plan);
    }

    private static SubscriptionResponse responseOf(WorkspaceSubscription subscription, BillingPlan plan) {
        return new SubscriptionResponse(subscription.getPlanCode(), subscription.getBillingInterval(),
                subscription.getSeats(), subscription.monthlyContactCredits(plan), subscription.getStatus(),
                subscription.getCurrentPeriodStart(), subscription.getCurrentPeriodEnd());
    }
}
