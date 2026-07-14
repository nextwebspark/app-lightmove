package app.lightmove.api.auth.application;

import app.lightmove.api.auth.domain.AuthProvider;
import app.lightmove.api.auth.domain.RevokeReason;
import app.lightmove.api.auth.domain.User;
import app.lightmove.api.auth.domain.UserIdentity;
import app.lightmove.api.auth.domain.UserStatus;
import app.lightmove.api.auth.infrastructure.UserIdentityRepository;
import app.lightmove.api.auth.infrastructure.UserRepository;
import app.lightmove.api.common.audit.AuditEventType;
import app.lightmove.api.common.audit.AuditService;
import app.lightmove.api.common.config.LightMoveProperties;
import app.lightmove.api.common.error.ApiException;
import app.lightmove.api.common.error.ErrorCode;
import app.lightmove.api.common.ratelimit.RateLimitGuard;
import app.lightmove.api.email.EmailAddressValidator;
import app.lightmove.api.workspace.domain.MemberStatus;
import app.lightmove.api.workspace.domain.WorkspaceMember;
import app.lightmove.api.workspace.infrastructure.WorkspaceMemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration and sign-in.
 *
 * <p>Signup creates a user and stops there — deliberately. A user's email domain says which <i>firm</i>
 * they work at, but not which <i>workspace</i> they belong to, because one firm may run several. So
 * step 2 shows them the workspaces already on their domain and lets them ask to join one (an admin
 * approves) or start their own. That lives in {@code OnboardingService}.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** Recorded against the user, so we can prove later what they agreed to. */
    private static final String PRIVACY_POLICY_VERSION = "2026-07-01";

    private final UserRepository users;
    private final UserIdentityRepository identities;
    private final WorkspaceMemberRepository members;
    private final PasswordPolicy passwords;
    private final TokenService tokens;
    private final VerificationService verification;
    private final EmailAddressValidator emailValidator;
    private final RateLimitGuard rateLimit;
    private final AuditService audit;
    private final LightMoveProperties.Auth config;

    public AuthService(UserRepository users, UserIdentityRepository identities,
                       WorkspaceMemberRepository members,
                       PasswordPolicy passwords, TokenService tokens, VerificationService verification,
                       EmailAddressValidator emailValidator, RateLimitGuard rateLimit,
                       AuditService audit, LightMoveProperties properties) {
        this.users = users;
        this.identities = identities;
        this.members = members;
        this.passwords = passwords;
        this.tokens = tokens;
        this.verification = verification;
        this.emailValidator = emailValidator;
        this.rateLimit = rateLimit;
        this.audit = audit;
        this.config = properties.auth();
    }

    /**
     * Signup step 1 — create the account.
     *
     * <p>Creates the user and nothing else. Which workspace they end up in is step 2's problem: they
     * will be shown the workspaces already on their email domain and can ask to join one (an admin
     * approves) or create their own. See {@code OnboardingService}.
     */
    @Transactional
    public AuthenticatedSession signup(SignupCommand command, HttpServletRequest request) {
        String email = normalise(command.email());
        rateLimit.checkSignup(email, request);

        if (!command.termsAccepted()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Terms must be accepted");
        }

        String passwordProblem = passwords.validate(command.password());
        if (passwordProblem != null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, passwordProblem);
        }

        // Rejects consumer providers (when configured to), disposable inboxes, and domains that cannot
        // receive mail at all.
        String domain = emailValidator.validateWorkEmail(email);

        if (users.existsByEmail(email)) {
            audit.event(AuditEventType.USER_SIGNED_UP).failed().from(request)
                    .reason("email_already_registered").detail("email", email).record();
            throw ApiException.of(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        Instant now = Instant.now();
        User user = users.save(User.registerLocal(
                email,
                passwords.hash(command.password()),
                command.fullName().trim(),
                now,
                PRIVACY_POLICY_VERSION));

        identities.save(UserIdentity.link(user.getId(), AuthProvider.LOCAL, email, email));

        if (config.autoVerifyEmail()) {
            // Dev shortcut. The user is managed and we are in a transaction, so this flushes with it, and
            // it happens before the token is issued below — so the session handed back already carries
            // emailVerified, and there is no second login.
            user.markEmailVerified(now);
            log.warn("lightmove.auth.auto-verify-email is ON — {} was verified without proving the address",
                    email);
        } else {
            verification.sendVerificationEmail(user, request);
        }

        log.info("User {} signed up on domain {}", user.getId(), domain);
        audit.event(AuditEventType.USER_SIGNED_UP)
                .actor(user.getId()).from(request).detail("domain", domain).record();

        // No workspace yet — the token carries no wsId claim, so the filter chain admits them only to
        // the onboarding endpoints, which is exactly where the wizard sends them next.
        return tokens.issue(user, null, request);
    }

    /**
     * Sign in with an email and password.
     *
     * <p>Every failure below returns the same {@link ErrorCode#INVALID_CREDENTIALS} — unknown address,
     * wrong password, or a Google-only account with no password to check. Distinguishing them would
     * hand an attacker an account-enumeration oracle: feed in a leaked email list and learn which
     * addresses are LightMove customers, without ever guessing a password. The audit log records
     * precisely which case it was; the caller is told only that the pair did not match.
     *
     * <p><b>{@code noRollbackFor = ApiException.class}, and the lockout depends on it.</b> Spring rolls
     * a transaction back on any unchecked exception, and {@link ApiException} is one. So the failed
     * attempt below would increment {@code failedLoginAttempts}, throw INVALID_CREDENTIALS, and have
     * the increment rolled straight back out again — for every attempt, forever. The counter would sit
     * at zero, the threshold would never be reached, and the account lockout would silently not exist.
     * An integration test that actually made five bad guesses is what caught this; nothing about the
     * code looked wrong.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthenticatedSession login(String rawEmail, String password, HttpServletRequest request) {
        String email = normalise(rawEmail);
        rateLimit.checkLogin(email, request);

        Optional<User> found = users.findByEmail(email);
        if (found.isEmpty()) {
            audit.event(AuditEventType.LOGIN_FAILED).failed().from(request)
                    .reason("no_such_user").detail("email", email).record();
            throw ApiException.of(ErrorCode.INVALID_CREDENTIALS);
        }

        User user = found.get();
        Instant now = Instant.now();

        // Checked before the password. Verifying the password of a locked account would let an
        // attacker keep testing guesses and learn, from the timing, when they finally hit the right
        // one — the lockout has to actually stop the work, not just suppress the answer.
        if (user.isLocked(now)) {
            audit.event(AuditEventType.LOGIN_FAILED).failed().actor(user.getId()).from(request)
                    .reason("account_locked").record();
            throw ApiException.of(ErrorCode.ACCOUNT_LOCKED);
        }

        if (user.getStatus() == UserStatus.SUSPENDED || user.getStatus() == UserStatus.DELETED) {
            audit.event(AuditEventType.LOGIN_FAILED).failed().actor(user.getId()).from(request)
                    .reason("status_" + user.getStatus()).record();
            throw ApiException.of(ErrorCode.ACCOUNT_SUSPENDED);
        }

        if (!passwords.matches(password, user.getPasswordHash())) {
            user.recordFailedLogin(now, config.lockout().maxFailedAttempts(), config.lockout().duration());

            // hasPassword() distinguishes a wrong password from a Google-only account. The user is
            // told neither; the ledger records which.
            audit.event(AuditEventType.LOGIN_FAILED).failed().actor(user.getId()).from(request)
                    .reason(user.hasPassword() ? "bad_password" : "no_local_password")
                    .detail("failedAttempts", user.getFailedLoginAttempts())
                    .record();

            if (user.isLocked(now)) {
                log.warn("Account {} locked after {} failed attempts", user.getId(), user.getFailedLoginAttempts());
                audit.event(AuditEventType.ACCOUNT_LOCKED).failed().actor(user.getId()).from(request)
                        .detail("until", String.valueOf(user.getLockedUntil())).record();
            }

            throw ApiException.of(ErrorCode.INVALID_CREDENTIALS);
        }

        user.recordSuccessfulLogin(now);
        audit.event(AuditEventType.LOGIN_SUCCEEDED).actor(user.getId()).from(request).record();

        // Null for a user who has not finished onboarding — the token then carries no tenant claim.
        WorkspaceMember membership = activeMembership(user.getId()).orElse(null);
        return tokens.issue(user, membership, request);
    }

    /**
     * Redeems a refresh token for a new session. Rotation and reuse detection live in
     * {@link TokenService}.
     *
     * <p>{@code noRollbackFor} here too: this is the outer transaction, and it would roll back the
     * inner one's family revocation on its way out. See {@link TokenService#rotate}.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthenticatedSession refresh(String refreshToken, HttpServletRequest request) {
        return tokens.rotate(refreshToken, request, users::findById, this::activeMembership);
    }

    @Transactional
    public void logout(String refreshToken, HttpServletRequest request) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            tokens.revoke(refreshToken, request);
        }
    }

    /** Signs the user out of every session. Whoever knew the old password is out. */
    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword, HttpServletRequest request) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.of(ErrorCode.INVALID_CREDENTIALS));

        if (user.hasPassword() && !passwords.matches(currentPassword, user.getPasswordHash())) {
            audit.event(AuditEventType.PASSWORD_CHANGED).failed().actor(userId).from(request)
                    .reason("bad_current_password").record();
            throw ApiException.of(ErrorCode.INVALID_CREDENTIALS);
        }

        String problem = passwords.validate(newPassword);
        if (problem != null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, problem);
        }

        user.changePassword(passwords.hash(newPassword));
        tokens.revokeAllSessions(userId, RevokeReason.PASSWORD_CHANGED);

        audit.event(AuditEventType.PASSWORD_CHANGED).actor(userId).from(request).record();
    }

    @Transactional(readOnly = true)
    public Optional<WorkspaceMember> activeMembership(UUID userId) {
        return members.findByUserIdAndStatus(userId, MemberStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public User requireUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.of(ErrorCode.INVALID_CREDENTIALS));
    }

    /** Lower-cased and trimmed. The column is citext, so this is belt and braces — but cheap. */
    private static String normalise(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
