package app.lightmove.api.billing.credit.controller;

import app.lightmove.api.billing.credit.dto.CreditGrantRequest;
import app.lightmove.api.billing.credit.dto.CreditGrantResponse;
import app.lightmove.api.billing.credit.service.PlatformCreditGrantService;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.PlatformAction;
import app.lightmove.api.core.security.rbac.RequirePlatformPermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** A platform admin granting a workspace contact credits by hand. The workspace is named in the path, never the caller's. */
@RestController
@RequiredArgsConstructor
public class PlatformCreditGrantController {

    private final PlatformCreditGrantService grants;

    @PostMapping("/api/v1/platform/workspaces/{workspaceId}/credit-grants")
    @RequirePlatformPermission(PlatformAction.CREDIT_GRANT)
    @ResponseStatus(HttpStatus.CREATED)
    public CreditGrantResponse grant(@AuthenticationPrincipal AuthPrincipal principal,
                                     @PathVariable UUID workspaceId,
                                     @Valid @RequestBody CreditGrantRequest request,
                                     HttpServletRequest httpRequest) {
        return grants.grant(principal.userId(), workspaceId, request, httpRequest);
    }
}
