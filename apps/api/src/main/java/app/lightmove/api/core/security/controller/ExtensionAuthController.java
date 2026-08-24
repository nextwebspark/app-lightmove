package app.lightmove.api.core.security.controller;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.dto.ExtensionRefreshRequest;
import app.lightmove.api.core.security.dto.ExtensionSessionResponse;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.model.AuthenticatedSession;
import app.lightmove.api.core.security.service.AuthenticationService;
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
 * <p>LightMove Capture runs on {@code chrome-extension://…} — a different origin from the app. The
 * refresh cookie is {@code SameSite=Strict}, host-only and scoped to {@code /api/v1/auth} precisely so
 * that no other origin can present it, and the way to let an extension use it would be to take those
 * attributes off. So the extension is <b>paired</b> instead: the signed-in web app asks for a refresh
 * token of its own and hands it over, and the extension comes back here to exchange it.
 *
 * <p>Three routes, and they are gated differently on purpose:
 *
 * <ul>
 *   <li>{@code /tokens} <b>mints a credential</b>, so it is the one route here that requires an
 *       authenticated caller. Only a signed-in user may pair their own account, and the account paired
 *       is the principal's — never one the request names.
 *   <li>{@code /refresh} and {@code /logout} carry the refresh token in the body and nothing else. The
 *       token <i>is</i> the credential, so they are public, and they are CSRF-exempt for the reason
 *       {@code SecurityConfig} gives: CSRF exists because a browser attaches a cookie automatically,
 *       and there is no cookie here for it to attach.
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/auth/extension")
@RequiredArgsConstructor
public class ExtensionAuthController {

    private final AuthenticationService authentication;
    private final AuthResponseAssembler assembler;

    /**
     * Pairs the extension with the caller's account and returns its refresh token.
     *
     * <p>Called by the web app's {@code /extension/connect} page, which forwards the token to the
     * extension. The response also carries a usable access token, so the extension can act at once
     * rather than immediately spending its brand-new refresh token to get one.
     */
    @PostMapping("/tokens")
    public ResponseEntity<ExtensionSessionResponse> pair(@AuthenticationPrincipal AuthPrincipal principal,
                                                         HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toSession(authentication.pairExtension(principal.userId(), httpRequest)));
    }

    /**
     * Exchanges the extension's refresh token for a new session, rotating it.
     *
     * <p>The rotated token comes back in the body and the old one is dead the moment this returns, so
     * the extension must store the new one before it does anything else. Presenting a rotated token
     * again is read as theft and revokes the family — the extension re-pairs, the web session is
     * untouched.
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
        authentication.logout(request.refreshToken(), httpRequest);
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
