package app.lightmove.api.core.security.controller;

import app.lightmove.api.core.ratelimit.service.RateLimitGuard;
import app.lightmove.api.core.security.dto.MfaChangeRequest;
import app.lightmove.api.core.security.dto.MfaSetupResponse;
import app.lightmove.api.core.security.dto.MfaStatusResponse;
import app.lightmove.api.core.security.dto.RecoveryCodesResponse;
import app.lightmove.api.core.security.mfa.MfaService;
import app.lightmove.api.core.security.model.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Settings → Security's two-factor card, the signed-in person's own factor. Under {@code /auth}, so the refresh cookie
 * is on the request: turning it on or off keeps the session it was done from and ends the rest.
 */
@RestController
@RequestMapping("/api/v1/auth/mfa")
@RequiredArgsConstructor
public class MfaController {

    private final MfaService mfa;
    private final RateLimitGuard rateLimit;

    @GetMapping
    public MfaStatusResponse status(@AuthenticationPrincipal AuthPrincipal principal) {
        return mfa.status(principal.userId());
    }

    @PostMapping("/setup")
    public MfaSetupResponse startSetup(@AuthenticationPrincipal AuthPrincipal principal) {
        return mfa.startSetup(principal.userId());
    }

    @PostMapping("/enable")
    public RecoveryCodesResponse enable(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody MfaChangeRequest request,
            @CookieValue(name = "${lightmove.auth.cookie.name}", required = false) String refreshToken,
            HttpServletRequest httpRequest) {
        rateLimit.checkMfaChange(principal.email(), httpRequest);
        return mfa.enable(principal.userId(), request.currentPassword(), request.code(), refreshToken, httpRequest);
    }

    @PostMapping("/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody MfaChangeRequest request,
            @CookieValue(name = "${lightmove.auth.cookie.name}", required = false) String refreshToken,
            HttpServletRequest httpRequest) {
        rateLimit.checkMfaChange(principal.email(), httpRequest);
        mfa.disable(principal.userId(), request.currentPassword(), request.code(), refreshToken, httpRequest);
    }

    @PostMapping("/recovery-codes")
    public RecoveryCodesResponse regenerateRecoveryCodes(@AuthenticationPrincipal AuthPrincipal principal,
                                                         @Valid @RequestBody MfaChangeRequest request,
                                                         HttpServletRequest httpRequest) {
        rateLimit.checkMfaChange(principal.email(), httpRequest);
        return mfa.regenerateRecoveryCodes(principal.userId(), request.currentPassword(), request.code(), httpRequest);
    }
}
