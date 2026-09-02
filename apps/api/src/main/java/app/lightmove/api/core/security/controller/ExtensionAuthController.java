package app.lightmove.api.core.security.controller;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.dto.ExtensionRefreshRequest;
import app.lightmove.api.core.security.dto.ExtensionSessionResponse;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.model.AuthenticatedSession;
import app.lightmove.api.core.ratelimit.service.RateLimitGuard;
import app.lightmove.api.core.security.service.AuthenticationService;
import app.lightmove.api.core.security.token.SessionClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The browser extension's session, which is deliberately not the web app's.
 *
 * <p>LightMove Capture runs on a {@code chrome-extension://} origin, so it cannot be given the refresh
 * cookie — that cookie is {@code SameSite=Strict}, host-only and path-scoped, and letting another
 * origin present it means taking those attributes off. It is <b>paired</b> instead: the signed-in web
 * app mints a refresh token of the extension's own and hands it over.
 *
 * <p>{@code /tokens} mints a credential, so it alone requires an authenticated caller, and the account
 * paired is the principal's — never one the request names. {@code /refresh} and {@code /logout} carry
 * the token in the body, which is the whole credential and the reason they are CSRF-exempt.
 *
 * <p>All three refuse a family opened for a different client: {@code app_lm_refresh_token.client}
 * decides, so a cookie-only credential cannot be laundered into a body-carried one.
 */
@RestController
@RequestMapping("/api/v1/auth/extension")
@RequiredArgsConstructor
public class ExtensionAuthController {

    private final AuthenticationService authentication;
    private final AuthResponseAssembler assembler;
    private final RateLimitGuard rateLimit;

    /**
     * Pairs the extension with the caller's account and returns its refresh token, plus an access token
     * so the popup can act at once rather than immediately spending the refresh token it just got.
     */
    @PostMapping("/tokens")
    public ResponseEntity<ExtensionSessionResponse> pair(@AuthenticationPrincipal AuthPrincipal principal,
                                                         HttpServletRequest httpRequest) {
        // Rate-limited despite being authenticated; RateLimitGuard.checkExtensionPairing says why.
        rateLimit.checkExtensionPairing(principal.email(), httpRequest);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toSession(authentication.pairExtension(principal.userId(), httpRequest)));
    }

    /**
     * Exchanges the extension's refresh token for a new session, rotating it. The old token is dead the
     * moment this returns, so the extension must store the successor before it does anything else.
     */
    @PostMapping("/refresh")
    public ResponseEntity<ExtensionSessionResponse> refresh(@Valid @RequestBody ExtensionRefreshRequest request,
                                                            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(toSession(authentication.refreshExtension(request.refreshToken(), httpRequest)));
    }

    /** Ends the extension's session and leaves every other session alone. Idempotent. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody ExtensionRefreshRequest request,
                                       HttpServletRequest httpRequest) {
        authentication.logout(request.refreshToken(), httpRequest, SessionClient.BROWSER_EXTENSION);
        return ResponseEntity.noContent().build();
    }

    private ExtensionSessionResponse toSession(AuthenticatedSession session) {
        if (session.tokens().refreshToken() == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Extension session carried no refresh token");
        }
        return new ExtensionSessionResponse(
                session.tokens().accessToken(),
                session.tokens().accessTokenTtl().toSeconds(),
                session.tokens().refreshToken(),
                assembler.user(session.user(), session.membership()));
    }
}
