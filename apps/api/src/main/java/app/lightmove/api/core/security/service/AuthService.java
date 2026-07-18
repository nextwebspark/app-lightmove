package app.lightmove.api.core.security.service;
import app.lightmove.api.core.security.token.TokenService;
import app.lightmove.api.core.security.model.AuthenticatedSession;
import app.lightmove.api.core.security.model.SignupCommand;

import app.lightmove.api.core.security.constant.AuthProvider;
import app.lightmove.api.core.security.token.RevokeReason;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.model.UserIdentity;
import app.lightmove.api.core.security.constant.UserStatus;
import app.lightmove.api.core.security.repository.UserIdentityRepository;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.core.audit.constant.AuthEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.ratelimit.service.RateLimitGuard;
import app.lightmove.api.core.email.service.EmailAddressValidator;
import app.lightmove.api.workspace.constant.MemberStatus;
import app.lightmove.api.workspace.model.WorkspaceMember;
import app.lightmove.api.workspace.repository.WorkspaceMemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration and sign-in.
 *
 * <p>Signup creates a user and stops there — deliberately. A user's email domain says which <i>firm</i>
 * they work at, but not which <i>workspace</i>: one firm may run several, and membership is
 * invitation-only. So step 2 has them create their own workspace (they become its ADMIN); being invited
 * into an existing one is the other way in. See {@code OnboardingService} / {@code InvitationService}.
 */
@Service
@Slf4j
public class AuthService {

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
     * create their own (becoming its ADMIN), or accept an admin's invitation. See
     * {@code OnboardingService}.
     */
    @Transactional
    public AuthenticatedSession signup(SignupCommand command, HttpServletRequest request) {
        String email = EmailAddressValidator.normalise(command.email());
        rateLimit.checkSignup(email, request);

        if (!command.termsAccepted()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Terms must be accepted");
        }

        String passwordProblem = passwords.validate(command.password());
        if (passwordProblem != null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, passwordProblem);
        }

        String domain = emailValidator.validateWorkEmail(email);

        if (users.existsByEmail(email)) {
            audit.event(AuthEventType.USER_SIGNED_UP).failed().from(request)
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
        audit.event(AuthEventType.USER_SIGNED_UP)
                .actor(user.getId()).from(request).detail("domain", domain).record();

        // No workspace yet — the token carries no wsId claim, so the filter chain admits them only to
        // the onboarding endpoints, which is exactly where the wizard sends them next.
        return tokens.issue(user, null, request);
    }

    /**
     * Creates a local account whose email is already proven, for the invitation-accept path.
     *
     * <p>No verification email, and the account is marked verified straight away: the invitation token
     * was mailed only to this address, so holding it is the proof of the mailbox that verification would
     * otherwise establish. The safety hinge is that {@code rawEmail} is the invitation's, resolved from
     * the token server-side — never a client-supplied value — so the token can only ever mint the one
     * identity it was addressed to.
     *
     * <p>No {@code validateWorkEmail} either: the address was vetted as a work address when the
     * invitation was issued (see {@code InvitationService#invite}), and re-checking now could reject a
     * contractor whose domain rules have since changed.
     */
    @Transactional
    public User createVerifiedLocalUser(String rawEmail, String fullName, String rawPassword,
                                        HttpServletRequest request) {
        String email = EmailAddressValidator.normalise(rawEmail);

        String passwordProblem = passwords.validate(rawPassword);
        if (passwordProblem != null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, passwordProblem);
        }

        if (users.existsByEmail(email)) {
            audit.event(AuthEventType.USER_SIGNED_UP).failed().from(request)
                    .reason("email_already_registered").detail("email", email).record();
            throw ApiException.of(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        Instant now = Instant.now();
        User user = users.save(User.registerLocal(
                email, passwords.hash(rawPassword), fullName.trim(), now, PRIVACY_POLICY_VERSION));
        identities.save(UserIdentity.link(user.getId(), AuthProvider.LOCAL, email, email));

        // Verified before the session is issued, so the token handed back already carries emailVerified
        // — the invitee is in with no second step.
        user.markEmailVerified(now);

        log.info("User {} created via invitation", user.getId());
        audit.event(AuthEventType.USER_SIGNED_UP)
                .actor(user.getId()).from(request).detail("via", "invitation").record();

        return user;
    }

    /**
     * Sign in with an email and password.
     *
     * <p>Every failure returns the same {@link ErrorCode#INVALID_CREDENTIALS} — unknown address, wrong
     * password, or a Google-only account — so the endpoint is not an account-enumeration oracle. The
     * audit log records which case it was; the caller is told only that the pair did not match.
     *
     * <p><b>{@code noRollbackFor = ApiException.class}, and the lockout depends on it:</b> otherwise the
     * failed-attempt increment is rolled back with the thrown ApiException, the counter never climbs,
     * and account lockout silently does not exist.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthenticatedSession login(String rawEmail, String password, HttpServletRequest request) {
        String email = EmailAddressValidator.normalise(rawEmail);
        rateLimit.checkLogin(email, request);

        Optional<User> found = users.findByEmail(email);
        if (found.isEmpty()) {
            audit.event(AuthEventType.LOGIN_FAILED).failed().from(request)
                    .reason("no_such_user").detail("email", email).record();
            throw ApiException.of(ErrorCode.INVALID_CREDENTIALS);
        }

        User user = found.get();
        Instant now = Instant.now();

        // Before the password: a lockout must stop the work, not just suppress the answer, or timing
        // leaks when a guess against a locked account was the right one.
        if (user.isLocked(now)) {
            audit.event(AuthEventType.LOGIN_FAILED).failed().actor(user.getId()).from(request)
                    .reason("account_locked").record();
            throw ApiException.of(ErrorCode.ACCOUNT_LOCKED);
        }

        if (user.getStatus() == UserStatus.SUSPENDED || user.getStatus() == UserStatus.DELETED) {
            audit.event(AuthEventType.LOGIN_FAILED).failed().actor(user.getId()).from(request)
                    .reason("status_" + user.getStatus()).record();
            throw ApiException.of(ErrorCode.ACCOUNT_SUSPENDED);
        }

        if (!passwords.matches(password, user.getPasswordHash())) {
            user.recordFailedLogin(now, config.lockout().maxFailedAttempts(), config.lockout().duration());

            // hasPassword() distinguishes a wrong password from a Google-only account. The user is
            // told neither; the ledger records which.
            audit.event(AuthEventType.LOGIN_FAILED).failed().actor(user.getId()).from(request)
                    .reason(user.hasPassword() ? "bad_password" : "no_local_password")
                    .detail("failedAttempts", user.getFailedLoginAttempts())
                    .record();

            if (user.isLocked(now)) {
                log.warn("Account {} locked after {} failed attempts", user.getId(), user.getFailedLoginAttempts());
                audit.event(AuthEventType.ACCOUNT_LOCKED).failed().actor(user.getId()).from(request)
                        .detail("until", String.valueOf(user.getLockedUntil())).record();
            }

            throw ApiException.of(ErrorCode.INVALID_CREDENTIALS);
        }

        user.recordSuccessfulLogin(now);
        audit.event(AuthEventType.LOGIN_SUCCEEDED).actor(user.getId()).from(request).record();

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
            audit.event(AuthEventType.PASSWORD_CHANGED).failed().actor(userId).from(request)
                    .reason("bad_current_password").record();
            throw ApiException.of(ErrorCode.INVALID_CREDENTIALS);
        }

        String problem = passwords.validate(newPassword);
        if (problem != null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, problem);
        }

        user.changePassword(passwords.hash(newPassword));
        tokens.revokeAllSessions(userId, RevokeReason.PASSWORD_CHANGED);

        audit.event(AuthEventType.PASSWORD_CHANGED).actor(userId).from(request).record();
    }

    @Transactional(readOnly = true)
    public Optional<WorkspaceMember> activeMembership(UUID userId) {
        return members.findByUserIdAndStatus(userId, MemberStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public User requireUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.of(ErrorCode.INVALID_CREDENTIALS));
    }
}
