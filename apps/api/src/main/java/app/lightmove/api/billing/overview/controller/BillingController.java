package app.lightmove.api.billing.overview.controller;

import app.lightmove.api.billing.overview.dto.BillingResponse;
import app.lightmove.api.billing.overview.dto.BillingUsageResponse;
import app.lightmove.api.billing.overview.service.BillingOverviewService;
import app.lightmove.api.core.security.model.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Settings → Billing and the credit chip, read by any staff member of the caller's workspace. */
@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingOverviewService billing;

    @GetMapping
    @PreAuthorize("@workspaceAuthorizer.member(principal)")
    public BillingResponse get(@AuthenticationPrincipal AuthPrincipal principal) {
        return billing.overview(principal.userId(), principal.requireWorkspaceId());
    }

    @GetMapping("/usage")
    @PreAuthorize("@workspaceAuthorizer.member(principal)")
    public BillingUsageResponse usage(@AuthenticationPrincipal AuthPrincipal principal) {
        return billing.usage(principal.userId(), principal.requireWorkspaceId());
    }
}
