package app.lightmove.api.core.security.controller;

import app.lightmove.api.core.ratelimit.service.RateLimitGuard;
import app.lightmove.api.core.security.dto.PlatformStepUpRequest;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.PlatformTwoFactor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Where a platform admin enters the fresh two-factor code a high-impact action asked for. */
@RestController
@RequiredArgsConstructor
public class PlatformStepUpController {

    private final PlatformTwoFactor twoFactor;
    private final RateLimitGuard rateLimit;

    @PostMapping("/api/v1/platform/step-up")
    @PreAuthorize("@platformAuthorizer.holdsAnyAction(principal)")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@AuthenticationPrincipal AuthPrincipal principal,
                        @Valid @RequestBody PlatformStepUpRequest request,
                        HttpServletRequest httpRequest) {
        rateLimit.checkPlatformStepUp(principal.email(), httpRequest);
        twoFactor.confirm(principal.userId(), request.code(), httpRequest);
    }
}
