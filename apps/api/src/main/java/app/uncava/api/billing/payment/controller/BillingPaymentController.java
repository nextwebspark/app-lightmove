package app.uncava.api.billing.payment.controller;

import app.uncava.api.billing.payment.dto.BillingRedirectResponse;
import app.uncava.api.billing.payment.dto.CreditsCheckoutRequest;
import app.uncava.api.billing.payment.dto.SubscriptionCheckoutRequest;
import app.uncava.api.billing.payment.service.BillingCheckoutService;
import app.uncava.api.core.security.model.AuthPrincipal;
import app.uncava.api.core.security.rbac.RequireWorkspacePermission;
import app.uncava.api.core.security.rbac.WorkspaceAction;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The ways an admin pays: Checkout for the plan or a pack of credits, and the Customer Portal for the rest. */
@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class BillingPaymentController {

    private final BillingCheckoutService checkout;

    @PostMapping("/checkout/subscription")
    @RequireWorkspacePermission(WorkspaceAction.BILLING_MANAGE)
    public BillingRedirectResponse subscribe(@AuthenticationPrincipal AuthPrincipal principal,
                                             @Valid @RequestBody SubscriptionCheckoutRequest request,
                                             HttpServletRequest httpRequest) {
        return checkout.subscribe(principal.userId(), principal.requireWorkspaceId(), request, httpRequest);
    }

    @PostMapping("/checkout/credits")
    @RequireWorkspacePermission(WorkspaceAction.BILLING_MANAGE)
    public BillingRedirectResponse buyCredits(@AuthenticationPrincipal AuthPrincipal principal,
                                              @Valid @RequestBody CreditsCheckoutRequest request,
                                              HttpServletRequest httpRequest) {
        return checkout.buyCredits(principal.userId(), principal.requireWorkspaceId(), request, httpRequest);
    }

    @PostMapping("/portal")
    @RequireWorkspacePermission(WorkspaceAction.BILLING_MANAGE)
    public BillingRedirectResponse portal(@AuthenticationPrincipal AuthPrincipal principal,
                                          HttpServletRequest httpRequest) {
        return checkout.portal(principal.userId(), principal.requireWorkspaceId(), httpRequest);
    }
}
