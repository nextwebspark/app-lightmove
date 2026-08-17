package app.lightmove.api.core.security.controller;

import app.lightmove.api.core.security.dto.AuthResponse;
import app.lightmove.api.core.security.dto.ForgotPasswordRequest;
import app.lightmove.api.core.security.dto.LoginRequest;
import app.lightmove.api.core.security.dto.ResendVerificationRequest;
import app.lightmove.api.core.security.dto.ResetPasswordRequest;
import app.lightmove.api.core.security.dto.SignupRequest;
import app.lightmove.api.core.security.dto.UpdateProfileRequest;
import app.lightmove.api.core.security.dto.UserResponse;
import app.lightmove.api.core.security.service.AuthenticationService;
import app.lightmove.api.core.security.service.PasswordResetService;
import app.lightmove.api.core.security.service.UserProfileService;
import app.lightmove.api.core.security.model.AuthenticatedSession;
import app.lightmove.api.core.security.model.ProfileUpdateCommand;
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
import java.util.List;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

    private final AuthenticationService authentication;
    private final VerificationService verification;
    private final PasswordResetService passwordReset;
    private final UserProfileService userProfile;
    private final RefreshCookieFactory refreshCookie;
    private final RateLimitGuard rateLimit;
    private final AuthResponseAssembler assembler;

    /** Absent unless at least one OAuth client is configured. See SecurityConfig. */
    private final ObjectProvider<ClientRegistrationRepository> oauthRegistrations;

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
        AuthenticatedSession session = authentication.signup(
                new SignupCommand(request.fullName(), request.email(), request.password(), request.termsAccepted()),
                httpRequest);

        return respond(HttpStatus.CREATED, session);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest) {
        return respond(HttpStatus.OK,
                authentication.login(request.email(), request.password(), httpRequest));
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
            return respond(HttpStatus.OK, authentication.refresh(refreshToken, httpRequest));
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

        authentication.logout(refreshToken, httpRequest);

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
        return ResponseEntity.ok(assembler.user(user, membershipOf(user)));
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

    /**
     * Emails a password-reset link.
     *
     * <p>Always 202, even for an address we have never seen — same reasoning as {@code /verify/resend}:
     * confirming which addresses exist is a free account-enumeration oracle.
     */
    @PostMapping("/password/forgot")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                               HttpServletRequest httpRequest) {
        rateLimit.checkPasswordResetRequest(request.email(), httpRequest);
        passwordReset.requestReset(request.email(), httpRequest);
        return ResponseEntity.accepted().build();
    }

    /**
     * Redeems the emailed link and signs the user straight in — the token proved the mailbox, which is
     * everything a login would have proved. {@code respond} sets the refresh cookie, exactly as login.
     */
    @PostMapping("/password/reset")
    public ResponseEntity<AuthResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request,
                                                      HttpServletRequest httpRequest) {
        return respond(HttpStatus.OK, passwordReset.reset(request.token(), request.password(), httpRequest));
    }

    /** The current user. The SPA calls this on boot to rehydrate. */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me() {
        AuthPrincipal principal = CurrentUser.require();
        User user = authentication.requireUser(principal.userId());
        return ResponseEntity.ok(assembler.user(user, membershipOf(user)));
    }

    /**
     * Settings → Profile.
     *
     * <p>Same URL as the {@code GET}, because it is the same resource: the caller themselves. Which
     * user is edited comes from the principal and never from the request, so there is nothing here to
     * authorise — and no {@code @PreAuthorize}, exactly as on {@code GET /me}.
     *
     * <p>Two consequences of living on the auth chain, both intended. It is <b>CSRF-protected</b> like
     * every other state change under {@code /auth} — the SPA sends the double-submit header. And it is
     * authenticated but <b>not verified-email gated</b>: this row is the caller's own account, not
     * tenant data, and someone still waiting on their inbox may already type their name into signup.
     */
    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request,
                                                      HttpServletRequest httpRequest) {
        AuthPrincipal principal = CurrentUser.require();
        User user = userProfile.update(
                principal.userId(),
                principal.workspaceId(),
                new ProfileUpdateCommand(
                        request.fullName(), request.title(), request.timezone(), request.locale()),
                httpRequest);

        return ResponseEntity.ok(assembler.user(user, membershipOf(user)));
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
     * Which sign-in methods this deployment actually offers, as the OAuth registration ids.
     *
     * <p>The frontend asks rather than assumes — a "Continue with LinkedIn" button that leads to a 404
     * is worse than no button. It returns <i>ids</i> and not a fixed set of flags so that wiring up
     * another provider stays a yml block: the id is the button and the authorisation path
     * ({@code /oauth2/authorization/{id}}), and the SPA falls back to a generic label for one it has
     * no icon for.
     */
    @GetMapping("/providers")
    public ResponseEntity<AuthProviders> providers() {
        ClientRegistrationRepository registrations = oauthRegistrations.getIfAvailable();

        // Only the in-memory repository can be enumerated; a lazily-resolving one cannot be asked
        // what it holds, and offering no buttons beats offering a broken one.
        List<String> configured = registrations instanceof Iterable<?> iterable
                ? StreamSupport.stream(iterable.spliterator(), false)
                        .map(registration -> ((ClientRegistration) registration).getRegistrationId())
                        .sorted()
                        .toList()
                : List.of();

        return ResponseEntity.ok(new AuthProviders(configured));
    }

    public record AuthProviders(List<String> providers) {
    }

    private WorkspaceMember membershipOf(User user) {
        return authentication.activeMembership(user.getId()).orElse(null);
    }

    /** The one place the refresh token is written to a cookie, and why it never reaches a response body. */
    private ResponseEntity<AuthResponse> respond(HttpStatus status, AuthenticatedSession session) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshCookie.create(session.tokens().refreshToken()).toString())
                .body(assembler.assemble(session.tokens(), session.user(), session.membership()));
    }
}
