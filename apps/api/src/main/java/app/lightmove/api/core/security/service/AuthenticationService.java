package app.lightmove.api.core.security.service;
import app.lightmove.api.core.security.token.SessionClient;
import app.lightmove.api.core.security.token.TokenService;
import app.lightmove.api.core.security.model.AuthenticatedSession;
import app.lightmove.api.core.security.model.SignupCommand;

import app.lightmove.api.core.security.token.RevokeReason;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.model.UserIdentity;
import app.lightmove.api.core.security.constant.UserStatus;
import app.lightmove.api.core.security.repository.UserIdentityRepository;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.core.audit.constant.AuthEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.AuthSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.ratelimit.service.RateLimitGuard;
import app.lightmove.api.core.email.service.EmailAddressValidator;
import app.lightmove.api.core.email.service.EmailSender;
import app.lightmove.api.core.email.service.EmailTemplates;
import app.lightmove.api.workspace.model.WorkspaceMember;
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
 * <p>Signup creates a user and stops there, deliberately: an email domain says which <i>firm</i>
 * someone works at, not which <i>workspace</i>, since one firm may run several and membership is
 * invitation-only. The wizard's organisation step has them create their own or accept an invitation,
 * and a session opens in whichever of their workspaces {@link WorkspaceSelection} settles on.
 */
@Service
@Slf4j
public class AuthenticationService {

    /** Recorded against the user, so we can prove later what they agreed to. */
    private static final String PRIVACY_POLICY_VERSION = "2026-07-01";

    private final UserRepository users;
    private final UserIdentityRepository identities;
    private final WorkspaceSelection selection;
    private final PasswordPolicy passwords;
    private final TokenService tokens;
    private final VerificationService verification;
    private final EmailAddressValidator emailValidator;
    private final RateLimitGuard rateLimit;
    private final AuditService audit;
    private final EmailSender emailSender;
    private final EmailTemplates templates;
    private final AuthSettings config;

    public AuthenticationService(UserRepository users, UserIdentityRepository identities,
                                 WorkspaceSelection selection, PasswordPolicy passwords,
                                 TokenService tokens, VerificationService verification,
                                 EmailAddressValidator emailValidator, RateLimitGuard rateLimit,
                                 AuditService audit, EmailSender emailSender,
                                 EmailTemplates templates, LightMoveProperties properties) {
        this.users = users;
        this.identities = identities;
        this.selection = selection;
        this.passwords = passwords;
        this.tokens = tokens;
        this.verification = verification;
        this.emailValidator = emailValidator;
        this.rateLimit = rateLimit;
        this.audit = audit;
        this.emailSender = emailSender;
        this.templates = templates;
        this.config = properties.auth();
    }

    /**
     * Signup step 1 — create the account and nothing else. Which workspace they end up in is step
     * 2's problem; see {@code OnboardingService}.
     */
    @Transactional
    public AuthenticatedSession signup(SignupCommand command, HttpServletRequest request) {
        String email = EmailAddressValidator.normalise(command.email());
        rateLimit.checkSignup(email, request);

        if (!command.termsAccepted()) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "termsAccepted",
                    "Terms must be accepted");
        }

        // As a field error, so the rule the encoder will enforce anyway reaches the form the same way
        // the DTO's own @Size does. Dropping it left a user retyping a password we had already refused.
        String passwordProblem = passwords.validate(command.password());
        if (passwordProblem != null) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "password", passwordProblem);
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

        identities.save(UserIdentity.link(user.getId(), UserIdentity.LOCAL_PROVIDER, email, email));

        if (config.autoVerifyEmail()) {
            // Dev shortcut, flushed before the token is issued below, so the session handed back
            // already carries emailVerified and there is no second login.
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
     * <p>No verification email: the invitation token was mailed only to this address, so holding it is
     * the mailbox proof verification would otherwise establish. The safety hinge is that
     * {@code rawEmail} is the invitation's, resolved from the token server-side and never a
     * client-supplied value, so the token can only mint the identity it was addressed to.
     *
     * <p>No {@code validateWorkEmail} either: the address was vetted when the invitation was issued,
     * and re-checking could reject a contractor whose domain rules have since changed.
     */
    @Transactional
    public User createVerifiedLocalUser(String rawEmail, String fullName, String rawPassword,
                                        HttpServletRequest request) {
        String email = EmailAddressValidator.normalise(rawEmail);

        String passwordProblem = passwords.validate(rawPassword);
        if (passwordProblem != null) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "password", passwordProblem);
        }

        if (users.existsByEmail(email)) {
            audit.event(AuthEventType.USER_SIGNED_UP).failed().from(request)
                    .reason("email_already_registered").detail("email", email).record();
            throw ApiException.of(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        Instant now = Instant.now();
        User user = users.save(User.registerLocal(
                email, passwords.hash(rawPassword), fullName.trim(), now, PRIVACY_POLICY_VERSION));
        identities.save(UserIdentity.link(user.getId(), UserIdentity.LOCAL_PROVIDER, email, email));

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
     * password, a Google-only account, a locked account, a suspended one — so the endpoint is not an
     * account-enumeration oracle. The audit log records which case it was; the caller is told only that
     * the pair did not match.
     *
     * <p>That includes the lockout, which used to answer its own 423. It was reachable only for an
     * address that exists, so five wrong guesses confirmed an account. A locked-out user is told by
     * <i>email</i> instead, which already proves ownership.
     *
     * <p>Every refusal also pays for one BCrypt comparison, via
     * {@link PasswordPolicy#equaliseFailureCost}: identical answers arriving in 26 ms and 276 ms are
     * not identical answers.
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
            passwords.equaliseFailureCost(password);
            audit.event(AuthEventType.LOGIN_FAILED).failed().from(request)
                    .reason("no_such_user").detail("email", email).record();
            throw ApiException.of(ErrorCode.INVALID_CREDENTIALS);
        }

        User user = found.get();
        Instant now = Instant.now();

        // Before the password: a lockout must stop the work, not just suppress the answer, or timing
        // leaks when a guess against a locked account was the right one. The decoy comparison replaces
        // the cost it skips, so the refusal is not faster than the wrong-password one it imitates.
        if (user.isLocked(now)) {
            passwords.equaliseFailureCost(password);
            audit.event(AuthEventType.LOGIN_FAILED).failed().actor(user.getId()).from(request)
                    .reason("account_locked").record();
            throw ApiException.of(ErrorCode.INVALID_CREDENTIALS);
        }

        if (user.getStatus() == UserStatus.SUSPENDED || user.getStatus() == UserStatus.DELETED) {
            passwords.equaliseFailureCost(password);
            audit.event(AuthEventType.LOGIN_FAILED).failed().actor(user.getId()).from(request)
                    .reason("status_" + user.getStatus()).record();
            throw ApiException.of(ErrorCode.INVALID_CREDENTIALS);
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

                // Here, not on every later refusal: the lock arms once, so the owner gets one mail per
                // window rather than one per guess an attacker makes. It is the only channel that can
                // say "you are locked out" — the login response deliberately cannot, since that would
                // confirm the account exists.
                emailSender.send(templates.buildAccountLockedEmail(
                        user.getEmail(), user.getFullName(), String.valueOf(user.getLockedUntil())));
            }

            throw ApiException.of(ErrorCode.INVALID_CREDENTIALS);
        }

        user.recordSuccessfulLogin(now);
        audit.event(AuthEventType.LOGIN_SUCCEEDED).actor(user.getId()).from(request).record();

        // Null for a user who has not finished onboarding — the token then carries no tenant claim.
        WorkspaceMember membership = selection.select(user, user.getLastWorkspaceId()).orElse(null);
        selection.remember(user, membership);
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
        return tokens.rotate(refreshToken, request, users::findById,
                (userId, sessionWorkspaceId) -> membershipForSession(userId, sessionWorkspaceId, request));
    }

    /**
     * Moves the session into another of the caller's workspaces — the only move a caller asks for; the
     * other is a web refresh falling through when the session's membership has ended. A miss is
     * {@code NOT_A_MEMBER}, the 404 a stranger gets, and burns nothing.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthenticatedSession switchWorkspace(UUID userId, UUID workspaceId, String refreshToken,
                                                HttpServletRequest request) {
        WorkspaceMember target = selection.membershipIn(userId, workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_A_MEMBER));

        // Checked before rotating, not after: rotate() commits even when this method then throws, and
        // a cookie rotated on someone else's behalf is a cookie its owner can no longer present.
        if (tokens.ownerOf(refreshToken).filter(userId::equals).isEmpty()) {
            throw new ApiException(ErrorCode.REFRESH_TOKEN_INVALID,
                    "Refresh cookie is not the bearer's, or does not exist");
        }

        AuthenticatedSession session = tokens.rotate(refreshToken, request, users::findById,
                (tokenUserId, ignoredSessionWorkspace) -> Optional.of(target));

        selection.remember(session.user(), target);
        audit.event(AuthEventType.WORKSPACE_SWITCHED).actor(userId).workspace(workspaceId).from(request)
                .record();
        return session;
    }

    @Transactional
    public void logout(String refreshToken, HttpServletRequest request) {
        logout(refreshToken, request, null);
    }

    /** Signs out, refusing a token whose family belongs to a different client. */
    @Transactional
    public void logout(String refreshToken, HttpServletRequest request, SessionClient client) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            tokens.revoke(refreshToken, request, client);
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
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "password", problem);
        }

        user.changePassword(passwords.hash(newPassword));
        tokens.revokeAllSessions(userId, RevokeReason.PASSWORD_CHANGED);

        audit.event(AuthEventType.PASSWORD_CHANGED).actor(userId).from(request).record();
    }

    /**
     * Pairs the browser extension with the signed-in user's account.
     *
     * <p>Opens a <b>new</b> refresh-token family rather than sharing the web session's, so revoking
     * one leaves the other alone — that is what makes "sign out of the extension" and "sign out of the
     * browser" two separate decisions in Settings → Active sessions. The token comes back in the
     * response body because the extension is a different origin and cannot be given a cookie scoped to
     * this one.
     *
     * <p>Pairing again <b>replaces</b> the extension session the account held rather than adding a
     * second: the page can mint without the extension ever receiving what it minted, and an abandoned
     * credential would otherwise stay live for its full TTL.
     */
    @Transactional
    public AuthenticatedSession pairExtension(UUID userId, UUID pairedWorkspaceId, HttpServletRequest request) {
        User user = requireUser(userId);
        tokens.revokeSessionsForClient(userId, SessionClient.BROWSER_EXTENSION, RevokeReason.SUPERSEDED);

        // The extension is paired into the workspace the web session is in, and its own family keeps
        // that workspace afterwards: switching the web app moves nothing here. Re-pairing does.
        WorkspaceMember membership = selection.select(user, pairedWorkspaceId).orElse(null);
        AuthenticatedSession paired = tokens.issue(user, membership, request, SessionClient.BROWSER_EXTENSION);

        audit.event(AuthEventType.EXTENSION_PAIRED).actor(userId).from(request).record();
        return paired;
    }

    /**
     * The extension's own refresh. Rotation, reuse detection and revocation are the ordinary ones —
     * only the TTL and the session label differ, and both come from the client passed here rather than
     * from anything the caller says about itself.
     *
     * <p>Exact, never falling through like a web refresh: a capture filed after a removal must not land
     * in another firm's workspace. The session loses its tenant claim until the extension is re-paired.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthenticatedSession refreshExtension(String refreshToken, HttpServletRequest request) {
        return tokens.rotate(refreshToken, request, users::findById, selection::membershipIn,
                SessionClient.BROWSER_EXTENSION);
    }

    /**
     * The session's workspace if the user is still in it, else wherever {@link WorkspaceSelection}
     * lands. Leaving a workspace this way is a move nobody asked for, so it is audited like a switch;
     * the SPA sees the workspace change in the refresh answer and resets as a switch does.
     */
    private Optional<WorkspaceMember> membershipForSession(UUID userId, UUID sessionWorkspaceId,
                                                           HttpServletRequest request) {
        Optional<WorkspaceMember> current = selection.membershipIn(userId, sessionWorkspaceId);
        if (current.isPresent()) {
            return current;
        }
        Optional<WorkspaceMember> landed = users.findById(userId).flatMap(user -> selection.select(user, null));
        if (sessionWorkspaceId != null) {
            audit.event(AuthEventType.WORKSPACE_SWITCHED).actor(userId)
                    .workspace(landed.map(WorkspaceMember::getWorkspaceId).orElse(null))
                    .target("WORKSPACE", sessionWorkspaceId)
                    .reason("MEMBERSHIP_ENDED")
                    .from(request)
                    .record();
        }
        return landed;
    }

    @Transactional(readOnly = true)
    public User requireUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.of(ErrorCode.INVALID_CREDENTIALS));
    }
}
