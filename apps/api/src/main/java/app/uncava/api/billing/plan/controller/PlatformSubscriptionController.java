package app.uncava.api.billing.plan.controller;

import app.uncava.api.billing.plan.dto.InvoicedSubscriptionRequest;
import app.uncava.api.billing.plan.dto.SubscriptionResponse;
import app.uncava.api.billing.plan.service.InvoicedSubscriptionService;
import app.uncava.api.core.security.model.AuthPrincipal;
import app.uncava.api.core.security.rbac.PlatformAction;
import app.uncava.api.core.security.rbac.RequirePlatformPermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** A platform admin setting what an invoiced workspace is on. */
@RestController
@RequiredArgsConstructor
public class PlatformSubscriptionController {

    private final InvoicedSubscriptionService subscriptions;

    @PutMapping("/api/v1/platform/workspaces/{workspaceId}/subscription")
    @RequirePlatformPermission(PlatformAction.SUBSCRIPTION_MANAGE)
    public SubscriptionResponse set(@AuthenticationPrincipal AuthPrincipal principal,
                                    @PathVariable UUID workspaceId,
                                    @Valid @RequestBody InvoicedSubscriptionRequest request,
                                    HttpServletRequest httpRequest) {
        return subscriptions.set(principal.userId(), workspaceId, request, httpRequest);
    }
}
