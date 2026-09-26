package app.lightmove.api.core.security.controller;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.ratelimit.service.RateLimitGuard;
import app.lightmove.api.core.security.dto.ExtensionRefreshRequest;
import app.lightmove.api.core.security.dto.ExtensionSessionResponse;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.model.AuthenticatedSession;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The browser extension's session, deliberately not the web app's: its origin cannot be given the
 * {@code SameSite=Strict} refresh cookie, so the signed-in web app <b>pairs</b> it with its own token.
 *
 * <p>{@code /tokens} pairs the principal's account, never one the request names. {@code /refresh} and
 * {@code /logout} carry the token in the body (hence CSRF-exempt), and all three refuse a family
 * opened for another client, so a cookie credential cannot be laundered into a body-carried one.
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
    public ExtensionSessionResponse refresh(@Valid @RequestBody ExtensionRefreshRequest request,
                                            HttpServletRequest httpRequest) {
        return toSession(authentication.refreshExtension(request.refreshToken(), httpRequest));
    }

    /** Ends the extension's session and leaves every other session alone. Idempotent. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody ExtensionRefreshRequest request,
                       HttpServletRequest httpRequest) {
        authentication.logout(request.refreshToken(), httpRequest, SessionClient.BROWSER_EXTENSION);
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
