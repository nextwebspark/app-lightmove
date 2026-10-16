package app.uncava.api.billing.overview.controller;

import app.uncava.api.billing.overview.dto.BillingResponse;
import app.uncava.api.billing.overview.dto.BillingUsageResponse;
import app.uncava.api.billing.overview.dto.PaymentCardResponse;
import app.uncava.api.billing.overview.service.BillingOverviewService;
import app.uncava.api.core.security.model.AuthPrincipal;
import app.uncava.api.core.security.rbac.RequireWorkspacePermission;
import app.uncava.api.core.security.rbac.WorkspaceAction;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Settings → Billing and the credit chip, read by any staff member of the caller's workspace; a 404 to a pure client. */
@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingOverviewService billing;

    @GetMapping
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public BillingResponse get(@AuthenticationPrincipal AuthPrincipal principal) {
        return billing.overview(principal.requireWorkspaceId(), principal.userId());
    }

    /** A billing manager's, like the portal the card is changed in. */
    @GetMapping("/card")
    @RequireWorkspacePermission(WorkspaceAction.BILLING_MANAGE)
    public PaymentCardResponse card(@AuthenticationPrincipal AuthPrincipal principal) {
        return billing.card(principal.requireWorkspaceId());
    }

    @GetMapping("/usage")
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public BillingUsageResponse usage(@AuthenticationPrincipal AuthPrincipal principal) {
        return billing.usage(principal.requireWorkspaceId());
    }
}
