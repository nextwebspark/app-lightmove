package app.lightmove.api.core.security.controller;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.ratelimit.service.RateLimitGuard;
import app.lightmove.api.core.security.dto.AuthResponse;
import app.lightmove.api.core.security.dto.ChangePasswordRequest;
import app.lightmove.api.core.security.dto.ForgotPasswordRequest;
import app.lightmove.api.core.security.dto.LoginRequest;
import app.lightmove.api.core.security.dto.ResendVerificationRequest;
import app.lightmove.api.core.security.dto.ResetPasswordRequest;
import app.lightmove.api.core.security.dto.SignupRequest;
import app.lightmove.api.core.security.dto.UpdateProfileRequest;
import app.lightmove.api.core.security.dto.UserResponse;
import app.lightmove.api.core.security.dto.VerifyEmailRequest;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.model.AuthenticatedSession;
import app.lightmove.api.core.security.model.ProfileUpdateCommand;
import app.lightmove.api.core.security.model.SignupCommand;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.service.AuthenticationService;
import app.lightmove.api.core.security.service.PasswordChangeService;
import app.lightmove.api.core.security.service.PasswordResetService;
import app.lightmove.api.core.security.service.UserProfileService;
import app.lightmove.api.core.security.service.VerificationService;
import app.lightmove.api.core.security.token.RefreshCookieFactory;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The auth endpoints. The refresh token belongs in an httpOnly cookie, never a response body. */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authentication;
    private final VerificationService verification;
    private final PasswordResetService passwordReset;
    private final PasswordChangeService passwordChange;
    private final UserProfileService userProfile;
    private final RefreshCookieFactory refreshCookie;
    private final RateLimitGuard rateLimit;
    private final AuthResponseAssembler assembler;

    /** Absent unless at least one OAuth client is configured. */
    private final ObjectProvider<ClientRegistrationRepository> oauthRegistrations;

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
            // A rejected token left in place is re-presented every page load, an endless stream of
            // TOKEN_REUSE_DETECTED. Set before the handler sees the exception, it survives onto the 401.
            httpResponse.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.expire().toString());
            throw e;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "${lightmove.auth.cookie.name}", required = false) String refreshToken,
            HttpServletRequest httpRequest) {

        authentication.logout(refreshToken, httpRequest);

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.expire().toString())
                .build();
    }

    @PostMapping("/verify")
    public ResponseEntity<AuthResponse> verify(@Valid @RequestBody VerifyEmailRequest request,
                                               HttpServletRequest httpRequest) {
        return respond(HttpStatus.OK, verification.verify(request.token(), httpRequest));
    }

    /** Always 202, even for an unknown address: anything else is an account-enumeration oracle. */
    @PostMapping("/verify/resend")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request,
                                                   HttpServletRequest httpRequest) {
        rateLimit.checkVerificationResend(request.email(), httpRequest);
        verification.resend(request.email(), httpRequest);
        return ResponseEntity.accepted().build();
    }

    /** Always 202, for {@code /verify/resend}'s reason. */
    @PostMapping("/password/forgot")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                               HttpServletRequest httpRequest) {
        rateLimit.checkPasswordResetRequest(request.email(), httpRequest);
        passwordReset.requestReset(request.email(), httpRequest);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password/reset")
    public ResponseEntity<AuthResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request,
                                                      HttpServletRequest httpRequest) {
        return respond(HttpStatus.OK, passwordReset.reset(request.token(), request.password(), httpRequest));
    }

    /** Answers a session because the change revokes every session, the caller's included. */
    @PostMapping("/password/change")
    public ResponseEntity<AuthResponse> changePassword(@AuthenticationPrincipal AuthPrincipal principal,
                                                       @Valid @RequestBody ChangePasswordRequest request,
                                                       HttpServletRequest httpRequest) {
        // The only brake on guessing the current password: it does not feed the login lockout counter.
        rateLimit.checkPasswordChange(principal.email(), httpRequest);

        return respond(HttpStatus.OK, passwordChange.change(
                principal.userId(), request.currentPassword(), request.newPassword(), httpRequest));
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        User user = authentication.requireUser(principal.userId());
        return assembler.user(user, membershipOf(user));
    }

    /** The user comes from the principal, never the request; not verified-email gated, as it is not tenant data. */
    @PatchMapping("/me")
    public UserResponse updateProfile(@AuthenticationPrincipal AuthPrincipal principal,
                                      @Valid @RequestBody UpdateProfileRequest request,
                                      HttpServletRequest httpRequest) {
        User user = userProfile.update(
                principal.userId(),
                principal.workspaceId(),
                new ProfileUpdateCommand(
                        request.fullName(), request.title(), request.timezone(), request.locale()),
                httpRequest);

        return assembler.user(user, membershipOf(user));
    }

    /**
     * <b>{@code token.getToken()} is not redundant.</b> Spring loads the CSRF token lazily and writes the
     * {@code XSRF-TOKEN} cookie only if something reads it; without the read every refresh 401s.
     */
    @GetMapping("/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void csrf(CsrfToken token) {
        token.getToken();
    }

    /** The configured OAuth registration ids, so a new provider stays a yml block. */
    @GetMapping("/providers")
    public AuthProviders providers() {
        ClientRegistrationRepository registrations = oauthRegistrations.getIfAvailable();

        // Only the in-memory repository can be enumerated; no buttons beats a broken one.
        List<String> configured = registrations instanceof Iterable<?> iterable
                ? StreamSupport.stream(iterable.spliterator(), false)
                        .map(registration -> ((ClientRegistration) registration).getRegistrationId())
                        .sorted()
                        .toList()
                : List.of();

        return new AuthProviders(configured);
    }

    public record AuthProviders(List<String> providers) {
    }

    private WorkspaceMember membershipOf(User user) {
        return authentication.activeMembership(user.getId()).orElse(null);
    }

    /** The one place the refresh token is written, to a cookie and never the body. */
    private ResponseEntity<AuthResponse> respond(HttpStatus status, AuthenticatedSession session) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshCookie.create(session.tokens().refreshToken()).toString())
                .body(assembler.assemble(session.tokens(), session.user(), session.membership()));
    }
}
