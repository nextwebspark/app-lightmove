package app.lightmove.api.core.security.service;

import app.lightmove.api.core.audit.constant.AuthEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.email.service.EmailAddressValidator;
import app.lightmove.api.core.email.service.EmailSender;
import app.lightmove.api.core.email.service.EmailTemplates;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.constant.AuthProvider;
import app.lightmove.api.core.security.constant.TokenPurpose;
import app.lightmove.api.core.security.constant.UserStatus;
import app.lightmove.api.core.security.model.AuthenticatedSession;
import app.lightmove.api.core.security.model.EmailVerifiedEvent;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.model.UserIdentity;
import app.lightmove.api.core.security.model.VerificationToken;
import app.lightmove.api.core.security.repository.UserIdentityRepository;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.core.security.repository.VerificationTokenRepository;
import app.lightmove.api.core.security.token.RevokeReason;
import app.lightmove.api.core.security.token.TokenService;
import app.lightmove.api.core.security.token.Tokens;
import app.lightmove.api.workspace.model.WorkspaceMember;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-service password recovery.
 *
 * <p>The emailed token <i>is</i> the credential: whoever holds it controls the mailbox, and the
 * mailbox is what the account is anchored to. That single fact drives every decision here — the
 * request side answers identically for known and unknown addresses (anything else is an
 * account-enumeration oracle), and the redeem side treats the token as proof strong enough to set a
 * password, clear a lockout, verify the address, and sign the user straight in.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private final UserRepository users;
    private final UserIdentityRepository identities;
    private final VerificationTokenRepository verificationTokens;
    private final PasswordPolicy passwords;
    private final TokenService tokens;
    private final AuthService auth;
    private final EmailSender emailSender;
    private final EmailTemplates templates;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final LightMoveProperties properties;

    /**
     * Issues a reset link and emails it.
     *
     * <p>Succeeds silently for an unknown address and for a suspended or deleted account — reporting
     * either would tell anyone with a list of addresses which ones are LightMove customers. Rate
     * limiting is applied by the caller, mirroring {@code /verify/resend}.
     *
     * <p>A Google-only account gets the email too: redeeming will <i>attach</i> a local password, which
     * is exactly the escape hatch {@link User#attachLocalPassword} exists for. And there is no
     * verified-email filter — an unverified creator may reset, because redeeming the link proves the
     * mailbox better than the verification email would have.
     */
    @Transactional
    public void requestReset(String email, HttpServletRequest request) {
        String normalised = EmailAddressValidator.normalise(email);

        users.findByEmail(normalised)
                .filter(user -> user.getStatus() != UserStatus.SUSPENDED
                        && user.getStatus() != UserStatus.DELETED)
                .ifPresentOrElse(
                        user -> sendResetEmail(user, request),
                        () -> log.debug("Password reset requested for unknown or ineligible address"));
    }

    /**
     * Redeems a reset link: sets the new password and signs the user in.
     *
     * <p>Signing in here is not a convenience bolted on — it is the same judgement the invitation-accept
     * flow already made: a token mailed only to this address, presented back to us, proves the mailbox,
     * and a proven mailbox is exactly what a login proves. Making the user re-type the password they
     * chose two seconds ago adds a step and no security.
     *
     * <p>Every failure is thrown <i>before</i> the token is consumed or any credential changes, so a
     * rejected attempt (weak password, suspended account) leaves the link redeemable and the account
     * untouched — no {@code noRollbackFor} needed, unlike {@code login()}.
     */
    @Transactional
    public AuthenticatedSession reset(String plaintextToken, String newPassword, HttpServletRequest request) {
        Instant now = Instant.now();

        VerificationToken token = verificationTokens.findByTokenHash(Tokens.hash(plaintextToken))
                .orElseThrow(() -> ApiException.of(ErrorCode.TOKEN_INVALID));

        if (!token.isRedeemable(now)) {
            // Consumed and expired are separated for the user's sake: "this link has expired" tells
            // them to request another, where "not valid" would leave them stuck guessing.
            throw ApiException.of(token.getConsumedAt() != null
                    ? ErrorCode.TOKEN_INVALID
                    : ErrorCode.TOKEN_EXPIRED);
        }
        if (token.getPurpose() != TokenPurpose.PASSWORD_RESET) {
            // The mirror of VerificationService's guard: a 24-hour verification token must never act
            // as a 30-minute password-changing credential.
            throw new ApiException(ErrorCode.TOKEN_INVALID, "Wrong token purpose: " + token.getPurpose());
        }

        User user = users.findById(token.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID, "Token references a missing user"));

        if (user.getStatus() == UserStatus.SUSPENDED || user.getStatus() == UserStatus.DELETED) {
            // Disclosure is fine here: the caller holds the emailed token, so the mailbox is theirs.
            audit.event(AuthEventType.PASSWORD_RESET_COMPLETED).failed().actor(user.getId()).from(request)
                    .reason("status_" + user.getStatus()).record();
            throw ApiException.of(ErrorCode.ACCOUNT_SUSPENDED);
        }

        String passwordProblem = passwords.validate(newPassword);
        if (passwordProblem != null) {
            // Before consume(): a weak password must not burn the link, or the user's only retry is
            // a fresh email round-trip.
            throw new ApiException(ErrorCode.VALIDATION_FAILED, passwordProblem);
        }

        token.consume(now);

        boolean attached = !user.hasPassword();
        if (attached) {
            // Google-only account gaining a local password. The LOCAL identity row keeps the signup
            // invariant "local password ⇔ LOCAL identity" intact.
            user.attachLocalPassword(passwords.hash(newPassword));
            if (identities.findByProviderAndProviderUserId(AuthProvider.LOCAL, user.getEmail()).isEmpty()) {
                identities.save(UserIdentity.link(user.getId(), AuthProvider.LOCAL, user.getEmail(), user.getEmail()));
            }
        } else {
            user.changePassword(passwords.hash(newPassword));
        }

        // A reset is the lockout recovery path — proving the mailbox beats waiting out the window —
        // and since we issue a session below, it genuinely is a login.
        user.recordSuccessfulLogin(now);

        boolean verifiedByReset = !user.isEmailVerified();
        if (verifiedByReset) {
            // The reset link is the same mailbox proof the verification email exists to collect.
            // Publishing the event is load-bearing: it synchronously materialises a held onboarding
            // (see EmailVerifiedEvent) — skip it and a held user stays dead-ended on the verify
            // screen with a verified address. It is also what lets the session issued below pass
            // the require-verified-email gate.
            user.markEmailVerified(now);
            events.publishEvent(new EmailVerifiedEvent(user.getId(), request));
        }

        // Whoever knew the old password is out — before issuing, so the fresh session survives.
        tokens.revokeAllSessions(user.getId(), RevokeReason.PASSWORD_CHANGED);

        log.info("Password reset completed for user {}", user.getId());
        audit.event(AuthEventType.PASSWORD_RESET_COMPLETED)
                .actor(user.getId())
                .from(request)
                .detail("via", attached ? "attach" : "change")
                .detail("emailVerifiedByReset", String.valueOf(verifiedByReset))
                .record();

        // Membership resolved after the event, so a workspace that just materialised lands in the
        // new token's claims.
        WorkspaceMember membership = auth.activeMembership(user.getId()).orElse(null);
        return tokens.issue(user, membership, request);
    }

    private void sendResetEmail(User user, HttpServletRequest request) {
        Instant now = Instant.now();
        // Burn outstanding reset tokens first, so three impatient clicks on "send link" leave exactly
        // one live credential in the inbox, not three.
        verificationTokens.consumeOutstanding(user.getId(), TokenPurpose.PASSWORD_RESET, now);

        String plaintext = Tokens.generate();
        verificationTokens.save(VerificationToken.issue(
                user.getId(),
                Tokens.hash(plaintext),
                TokenPurpose.PASSWORD_RESET,
                now.plus(properties.auth().passwordResetTokenTtl())));

        // The link points at the SPA, not at the API — the frontend owns the "choose a new password"
        // screen and calls the API from there.
        String link = "%s/auth/reset-password?token=%s".formatted(
                properties.web().baseUrl(),
                URLEncoder.encode(plaintext, StandardCharsets.UTF_8));

        emailSender.send(templates.buildPasswordResetEmail(user.getEmail(), user.getFullName(), link));

        audit.event(AuthEventType.PASSWORD_RESET_REQUESTED)
                .actor(user.getId())
                .from(request)
                .record();
    }
}
