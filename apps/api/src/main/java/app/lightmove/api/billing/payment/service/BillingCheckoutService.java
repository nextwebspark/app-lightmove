package app.lightmove.api.billing.payment.service;

import app.lightmove.api.billing.payment.dto.BillingRedirectResponse;
import app.lightmove.api.billing.payment.dto.CreditsCheckoutRequest;
import app.lightmove.api.billing.payment.dto.SubscriptionCheckoutRequest;
import app.lightmove.api.billing.payment.model.BillingCustomer;
import app.lightmove.api.billing.payment.model.CreditsCheckout;
import app.lightmove.api.billing.payment.model.SubscriptionCheckout;
import app.lightmove.api.billing.payment.repository.BillingCustomerRepository;
import app.lightmove.api.billing.plan.model.WorkspaceSubscription;
import app.lightmove.api.billing.plan.repository.WorkspaceSubscriptionRepository;
import app.lightmove.api.billing.plan.service.BillingWorkspaces;
import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.CreditPackSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Sends an admin to Stripe's Checkout or Customer Portal, outside any transaction. Nothing is granted here: only the
 * webhook, once Stripe says it was paid, grants credits.
 */
@Service
@RequiredArgsConstructor
public class BillingCheckoutService {

    private static final String BILLING_PAGE = "/settings/billing";

    private final PaymentGateway gateway;
    private final BillingCustomerRepository customers;
    private final PlanPrices prices;
    private final WorkspaceSubscriptionRepository subscriptions;
    private final BillingWorkspaces workspaces;
    private final WorkspaceAccess access;
    private final AuditService audit;
    private final LightMoveProperties properties;

    /** Quantity is the workspace's staff seats; a workspace already paying through Stripe changes plan in the portal. */
    public BillingRedirectResponse subscribe(UUID actorId, UUID workspaceId, SubscriptionCheckoutRequest request,
                                             HttpServletRequest httpRequest) {
        requireOffered();
        String priceId = prices.priceOf(request.planCode(), request.interval())
                .orElseThrow(() -> ApiException.of(ErrorCode.BILLING_PLAN_UNKNOWN));
        if (subscriptions.findByWorkspaceId(workspaceId).filter(WorkspaceSubscription::isBilledByStripe).isPresent()) {
            throw ApiException.of(ErrorCode.SUBSCRIPTION_BILLED_BY_STRIPE);
        }
        String customerId = customerOf(workspaceId);
        if (gateway.hasLiveSubscription(customerId)) {
            throw ApiException.of(ErrorCode.SUBSCRIPTION_BILLED_BY_STRIPE);
        }
        long seats = Math.max(1, access.activeStaff(workspaceId).size());
        String url = gateway.subscriptionCheckout(new SubscriptionCheckout(workspaceId, customerId, priceId, seats,
                returnUrl("?checkout=subscribed"), returnUrl("?checkout=cancelled")));
        audit.event(WorkspaceEventType.BILLING_CHECKOUT_STARTED).actor(actorId).workspace(workspaceId)
                .target("workspace", workspaceId)
                .detail("plan", request.planCode().name())
                .detail("billingInterval", request.interval().name())
                .detail("seats", seats)
                .from(httpRequest)
                .record();
        return new BillingRedirectResponse(url);
    }

    public BillingRedirectResponse buyCredits(UUID actorId, UUID workspaceId, CreditsCheckoutRequest request,
                                              HttpServletRequest httpRequest) {
        requireOffered();
        CreditPackSettings pack = properties.billing().packs().get(request.pack());
        if (pack == null || pack.stripePriceId() == null || pack.stripePriceId().isBlank()) {
            throw ApiException.of(ErrorCode.BILLING_PACK_UNKNOWN);
        }
        String url = gateway.creditsCheckout(new CreditsCheckout(workspaceId, customerOf(workspaceId), request.pack(),
                pack.stripePriceId(), returnUrl("?checkout=credits"), returnUrl("?checkout=cancelled")));
        audit.event(WorkspaceEventType.BILLING_CHECKOUT_STARTED).actor(actorId).workspace(workspaceId)
                .target("workspace", workspaceId)
                .detail("pack", request.pack())
                .detail("credits", pack.credits())
                .from(httpRequest)
                .record();
        return new BillingRedirectResponse(url);
    }

    public BillingRedirectResponse portal(UUID actorId, UUID workspaceId, HttpServletRequest httpRequest) {
        requireOffered();
        String url = gateway.portal(customerOf(workspaceId), returnUrl(""));
        audit.event(WorkspaceEventType.BILLING_PORTAL_OPENED).actor(actorId).workspace(workspaceId)
                .target("workspace", workspaceId)
                .from(httpRequest)
                .record();
        return new BillingRedirectResponse(url);
    }

    private String customerOf(UUID workspaceId) {
        return customers.findById(workspaceId).map(BillingCustomer::getStripeCustomerId).orElseGet(() -> {
            customers.insertIfAbsent(workspaceId, gateway.createCustomer(workspaceId, workspaces.nameOf(workspaceId)));
            return customers.findById(workspaceId).orElseThrow().getStripeCustomerId();
        });
    }

    private void requireOffered() {
        if (!gateway.isOffered()) {
            throw ApiException.of(ErrorCode.BILLING_UNAVAILABLE);
        }
    }

    private String returnUrl(String query) {
        return properties.web().baseUrl() + BILLING_PAGE + query;
    }
}
