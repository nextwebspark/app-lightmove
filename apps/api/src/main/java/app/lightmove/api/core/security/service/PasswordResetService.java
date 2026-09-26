package app.lightmove.api.core.security.service;

import app.lightmove.api.core.audit.constant.AuthEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.email.service.EmailAddressValidator;
import app.lightmove.api.core.email.service.EmailSender;
import app.lightmove.api.core.email.service.EmailTemplates;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.constant.TokenPurpose;
import app.lightmove.api.core.security.constant.UserStatus;
import app.lightmove.api.core.security.model.AuthenticatedSession;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-service password recovery. The emailed token is the mailbox proof: enough to set a password,
 * clear a lockout, verify the address and sign in. Requests answer identically for unknown addresses.
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
    private final AuthenticationService authentication;
    private final WorkspaceSelection selection;
    private final EmailSender emailSender;
    private final EmailTemplates templates;
    private final AuditService audit;
    private final LightMoveProperties properties;

    /**
     * Silent for an unknown, suspended or deleted account (an enumeration oracle otherwise). A
     * provider-only account gets the email too: redeeming attaches a local password.
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
     * Sets the new password and signs the user in. Every failure is thrown before the token is consumed,
     * so a rejected attempt leaves the link redeemable — no {@code noRollbackFor} needed, unlike login.
     */
    @Transactional
    public AuthenticatedSession reset(String plaintextToken, String newPassword, HttpServletRequest request) {
        Instant now = Instant.now();

        VerificationToken token = verificationTokens.findByTokenHash(Tokens.hash(plaintextToken))
                .orElseThrow(() -> ApiException.of(ErrorCode.TOKEN_INVALID));

        if (!token.isRedeemable(now)) {
            throw ApiException.of(token.getConsumedAt() != null
                    ? ErrorCode.TOKEN_INVALID
                    : ErrorCode.TOKEN_EXPIRED);
        }
        if (token.getPurpose() != TokenPurpose.PASSWORD_RESET) {
            // A 24-hour verification token must never act as a 30-minute password-changing credential.
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
            // Before consume(): a weak password must not burn the link.
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "password", passwordProblem);
        }

        token.consume(now);

        boolean attached = !user.hasPassword();
        if (attached) {
            // Keeps the invariant "local password ⇔ LOCAL identity".
            user.attachLocalPassword(passwords.hash(newPassword));
            if (identities.findByProviderAndProviderUserId(UserIdentity.LOCAL_PROVIDER, user.getEmail()).isEmpty()) {
                identities.save(UserIdentity.link(user.getId(), UserIdentity.LOCAL_PROVIDER, user.getEmail(), user.getEmail()));
            }
        } else {
            user.changePassword(passwords.hash(newPassword));
        }

        // A reset is the lockout recovery path, and it issues a session: it is a login.
        user.recordSuccessfulLogin(now);

        boolean verifiedByReset = !user.isEmailVerified();
        if (verifiedByReset) {
            user.markEmailVerified(now);
        }

        // Before issuing, so the fresh session survives.
        tokens.revokeAllSessions(user.getId(), RevokeReason.PASSWORD_CHANGED);

        log.info("Password reset completed for user {}", user.getId());
        audit.event(AuthEventType.PASSWORD_RESET_COMPLETED)
                .actor(user.getId())
                .from(request)
                .detail("via", attached ? "attach" : "change")
                .detail("emailVerifiedByReset", String.valueOf(verifiedByReset))
                .record();

        WorkspaceMember membership = selection.signIn(user);
        return tokens.issue(user, membership, request);
    }

    private void sendResetEmail(User user, HttpServletRequest request) {
        Instant now = Instant.now();
        // Three clicks on "send link" leave exactly one live credential, not three.
        verificationTokens.consumeOutstanding(user.getId(), TokenPurpose.PASSWORD_RESET, now);

        String plaintext = Tokens.generate();
        verificationTokens.save(VerificationToken.issue(
                user.getId(),
                Tokens.hash(plaintext),
                TokenPurpose.PASSWORD_RESET,
                now.plus(properties.auth().passwordResetTokenTtl())));

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
