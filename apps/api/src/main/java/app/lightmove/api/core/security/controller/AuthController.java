package app.lightmove.api.core.security.controller;

import app.lightmove.api.core.security.dto.AuthDtos.AuthResponse;
import app.lightmove.api.core.security.dto.AuthDtos.LoginRequest;
import app.lightmove.api.core.security.dto.AuthDtos.ResendVerificationRequest;
import app.lightmove.api.core.security.dto.AuthDtos.SignupRequest;
import app.lightmove.api.core.security.dto.AuthDtos.UserResponse;
import app.lightmove.api.core.security.service.AuthService;
import app.lightmove.api.core.security.model.AuthenticatedSession;
import app.lightmove.api.core.security.model.SignupCommand;
import app.lightmove.api.core.security.service.VerificationService;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.token.RefreshCookieFactory;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.ratelimit.service.RateLimitGuard;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.service.CurrentUser;
import app.lightmove.api.workspace.model.WorkspaceMember;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The auth endpoints.
 *
 * <p>Thin on purpose: this class translates HTTP to commands and back, and owns exactly one piece of
 * knowledge the services do not have — that the refresh token belongs in an httpOnly cookie and must
 * never appear in a response body.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService auth;
    private final VerificationService verification;
    private final RefreshCookieFactory refreshCookie;
    private final RateLimitGuard rateLimit;
    private final AuthResponseAssembler assembler;

    /** Absent unless a Google OAuth client is configured. See SecurityConfig. */
    private final ObjectProvider<ClientRegistrationRepository> googleRegistration;

    /**
     * Signup step 1.
     *
     * <p>Returns 201 with a session but <i>no workspace</i> — the user has an account and no
     * organisation yet. The token carries no tenant claim, so the filter chain admits them only to the
     * onboarding endpoints, which is precisely where the wizard is taking them next.
     */
    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request,
                                               HttpServletRequest httpRequest) {
        AuthenticatedSession session = auth.signup(
                new SignupCommand(request.fullName(), request.email(), request.password(), request.termsAccepted()),
                httpRequest);

        return respond(HttpStatus.CREATED, session);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest) {
        return respond(HttpStatus.OK, auth.login(request.email(), request.password(), httpRequest));
    }

    /**
     * Exchanges the refresh cookie for a new session.
     *
     * <p>The cookie is the credential — no bearer token required, which is the whole point: this is how
     * the SPA recovers a session after a page reload, once the in-memory access token is gone. It is
     * also why this is one of only two CSRF-protected routes (see {@code SecurityConfig}).
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = "${lightmove.auth.cookie.name}", required = false) String refreshToken,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ApiException(ErrorCode.REFRESH_TOKEN_INVALID, "No refresh cookie on the request");
        }

        try {
            return respond(HttpStatus.OK, auth.refresh(refreshToken, httpRequest));
        } catch (ApiException e) {
            // Expire the cookie on the way out: a rejected token is dead, and leaving it in place makes
            // the browser re-present it every page load — an endless stream of TOKEN_REUSE_DETECTED. The
            // header is set before the handler sees the exception, so it survives onto the 401.
            httpResponse.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.expire().toString());
            throw e;
        }
    }

    /** Revokes the refresh token and clears the cookie. Idempotent — signing out twice is not an error. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "${lightmove.auth.cookie.name}", required = false) String refreshToken,
            HttpServletRequest httpRequest) {

        auth.logout(refreshToken, httpRequest);

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.expire().toString())
                .build();
    }

    /**
     * Redeems a verification link.
     *
     * <p>Returns the updated user so the SPA can drop the "unverified" banner without a second
     * round-trip. It does not mint a new session: the access token the client holds still claims
     * {@code emailVerified: false}, and picks up the truth on its next refresh — within 15 minutes, or
     * immediately if the client asks for one.
     */
    @PostMapping("/verify")
    public ResponseEntity<UserResponse> verify(@RequestParam("token") String token,
                                               HttpServletRequest httpRequest) {
        User user = verification.verify(token, httpRequest);
        WorkspaceMember membership = auth.activeMembership(user.getId()).orElse(null);
        return ResponseEntity.ok(assembler.user(user, membership));
    }

    /**
     * Resends the verification email.
     *
     * <p>Always 202, even for an address we have never seen. Confirming which addresses exist would
     * turn this endpoint into a free account-enumeration oracle.
     */
    @PostMapping("/verify/resend")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request,
                                                   HttpServletRequest httpRequest) {
        rateLimit.checkVerificationResend(request.email(), httpRequest);
        verification.resend(request.email(), httpRequest);
        return ResponseEntity.accepted().build();
    }

    /** The current user. The SPA calls this on boot to rehydrate. */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me() {
        AuthPrincipal principal = CurrentUser.require();
        User user = auth.requireUser(principal.userId());
        WorkspaceMember membership = auth.activeMembership(user.getId()).orElse(null);
        return ResponseEntity.ok(assembler.user(user, membership));
    }

    /**
     * Hands the SPA a CSRF token before it calls {@code /refresh} or {@code /logout}.
     *
     * <p><b>{@code token.getToken()} must not be removed as redundant.</b> Spring loads the CSRF token
     * lazily and writes the {@code XSRF-TOKEN} cookie only if something reads it; skip the read and the
     * SPA has nothing to echo, so every refresh 401s. The cookie is the response, not the empty body.
     */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken();
        return ResponseEntity.noContent().build();
    }

    /**
     * Which sign-in methods this deployment actually offers.
     *
     * <p>Backs the {@code showSso} flag the Login mockup already has. Google is only wired when an
     * OAuth client is configured, so the frontend asks rather than assumes — a "Continue with Google"
     * button that leads to a 404 is worse than no button.
     */
    @GetMapping("/providers")
    public ResponseEntity<AuthProviders> providers() {
        return ResponseEntity.ok(new AuthProviders(googleRegistration.getIfAvailable() != null));
    }

    public record AuthProviders(boolean google) {
    }

    /** The one place the refresh token is written to a cookie, and why it never reaches a response body. */
    private ResponseEntity<AuthResponse> respond(HttpStatus status, AuthenticatedSession session) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshCookie.create(session.tokens().refreshToken()).toString())
                .body(assembler.assemble(session.tokens(), session.user(), session.membership()));
    }
}
