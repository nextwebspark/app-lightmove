package app.lightmove.api.core.security.service;
import app.lightmove.api.core.audit.constant.AuthEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.AuthSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.email.service.EmailAddressValidator;
import app.lightmove.api.core.email.service.EmailSender;
import app.lightmove.api.core.email.service.EmailTemplates;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.ratelimit.service.RateLimitGuard;
import app.lightmove.api.core.security.constant.PrivacyPolicy;
import app.lightmove.api.core.security.constant.UserStatus;
import app.lightmove.api.core.security.model.AuthenticatedSession;
import app.lightmove.api.core.security.model.SignupCommand;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.model.UserIdentity;
import app.lightmove.api.core.security.repository.UserIdentityRepository;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.core.security.token.RevokeReason;
import app.lightmove.api.core.security.token.SessionClient;
import app.lightmove.api.core.security.token.TokenService;
import app.lightmove.api.workspace.model.WorkspaceMember;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration and sign-in. Signup creates a user and stops there: a domain names a firm, not a
 * workspace, and membership is invitation-only.
 */
@Service
@Slf4j
public class AuthenticationService {

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

    /** Signup step 1: the account and nothing else; see {@code OnboardingService} for step 2. */
    @Transactional
    public AuthenticatedSession signup(SignupCommand command, HttpServletRequest request) {
        String email = EmailAddressValidator.normalise(command.email());
        rateLimit.checkSignup(email, request);

        if (!command.termsAccepted()) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "termsAccepted",
                    "Terms must be accepted");
        }

        // As a field error, so the form shows it; dropping it left a user retyping a refused password.
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
                PrivacyPolicy.CURRENT_VERSION));

        identities.save(UserIdentity.link(user.getId(), UserIdentity.LOCAL_PROVIDER, email, email));

        if (config.autoVerifyEmail()) {
            // Before the token is issued, so the session already carries emailVerified.
            user.markEmailVerified(now);
            log.warn("lightmove.auth.auto-verify-email is ON — {} was verified without proving the address",
                    email);
        } else {
            verification.sendVerificationEmail(user, request);
        }

        log.info("User {} signed up on domain {}", user.getId(), domain);
        audit.event(AuthEventType.USER_SIGNED_UP)
                .actor(user.getId()).from(request).detail("domain", domain).record();

        return tokens.issue(user, null, request);
    }

    /**
     * Creates a pre-verified local account for the invitation-accept path. Safe only because
     * {@code rawEmail} is the invitation's, resolved from the token server-side, never client-supplied.
     * No {@code validateWorkEmail}: the address was vetted when the invitation was issued.
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
                email, passwords.hash(rawPassword), fullName.trim(), now, PrivacyPolicy.CURRENT_VERSION));
        identities.save(UserIdentity.link(user.getId(), UserIdentity.LOCAL_PROVIDER, email, email));

        user.markEmailVerified(now);

        log.info("User {} created via invitation", user.getId());
        audit.event(AuthEventType.USER_SIGNED_UP)
                .actor(user.getId()).from(request).detail("via", "invitation").record();

        return user;
    }

    /**
     * Every failure — including the lockout, whose own 423 confirmed the account exists — answers
     * {@link ErrorCode#INVALID_CREDENTIALS} and pays one BCrypt comparison
     * ({@link PasswordPolicy#equaliseFailureCost}); a locked-out owner is told by email.
     *
     * <p><b>{@code noRollbackFor = ApiException.class}:</b> otherwise the failed-attempt increment rolls
     * back with the thrown exception and account lockout silently does not exist.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthenticatedSession login(String rawEmail, String password, HttpServletRequest request) {
        String email = EmailAddressValidator.normalise(rawEmail);
        rateLimit.checkLogin(email, request);

        Optional<User> found = users.findByEmail(email);
        if (found.isEmpty()) {
            throw refuseLogin(password, audit.event(AuthEventType.LOGIN_FAILED).failed().from(request)
                    .reason("no_such_user").detail("email", email));
        }

        User user = found.get();
        Instant now = Instant.now();

        // Before the password: a lockout must stop the work, not just suppress the answer, or timing
        // leaks when a guess against a locked account was the right one. The decoy comparison replaces
        // the cost it skips, so the refusal is not faster than the wrong-password one it imitates.
        if (user.isLocked(now)) {
            throw refuseLogin(password, audit.event(AuthEventType.LOGIN_FAILED).failed().actor(user.getId())
                    .from(request).reason("account_locked"));
        }

        if (user.getStatus() == UserStatus.SUSPENDED || user.getStatus() == UserStatus.DELETED) {
            throw refuseLogin(password, audit.event(AuthEventType.LOGIN_FAILED).failed().actor(user.getId())
                    .from(request).reason("status_" + user.getStatus()));
        }

        if (!passwords.matches(password, user.getPasswordHash())) {
            user.recordFailedLogin(now, config.lockout().maxFailedAttempts(), config.lockout().duration());

            audit.event(AuthEventType.LOGIN_FAILED).failed().actor(user.getId()).from(request)
                    .reason(user.hasPassword() ? "bad_password" : "no_local_password")
                    .detail("failedAttempts", user.getFailedLoginAttempts())
                    .record();

            if (user.isLocked(now)) {
                log.warn("Account {} locked after {} failed attempts", user.getId(), user.getFailedLoginAttempts());
                audit.event(AuthEventType.ACCOUNT_LOCKED).failed().actor(user.getId()).from(request)
                        .detail("until", String.valueOf(user.getLockedUntil())).record();

                // Here, not on every later refusal: one mail per lock window, not one per guess.
                emailSender.send(templates.buildAccountLockedEmail(
                        user.getEmail(), user.getFullName(), String.valueOf(user.getLockedUntil())));
            }

            throw ApiException.of(ErrorCode.INVALID_CREDENTIALS);
        }

        user.recordSuccessfulLogin(now);
        audit.event(AuthEventType.LOGIN_SUCCEEDED).actor(user.getId()).from(request).record();

        return tokens.issue(user, selection.signIn(user), request);
    }

    /** A refusal ahead of the password check: it pays the BCrypt cost it skips, records why, and says nothing. */
    private ApiException refuseLogin(String password, AuditService.Builder failure) {
        passwords.equaliseFailureCost(password);
        failure.record();
        return ApiException.of(ErrorCode.INVALID_CREDENTIALS);
    }

    /** {@code noRollbackFor} here too: as the outer transaction it would roll back the family revocation. */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthenticatedSession refresh(String refreshToken, HttpServletRequest request) {
        return tokens.rotate(refreshToken, request, users::findById,
                (userId, sessionWorkspaceId) -> membershipForSession(userId, sessionWorkspaceId, request));
    }

    /**
     * The one move a caller asks for; the other is a web refresh whose membership ended. A miss is
     * {@code NOT_A_MEMBER}, the 404 a stranger gets, and burns nothing.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthenticatedSession switchWorkspace(UUID userId, UUID workspaceId, String refreshToken,
                                                HttpServletRequest request) {
        WorkspaceMember target = selection.membershipIn(userId, workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_A_MEMBER));

        // Before rotating: rotate() commits even when this then throws, burning the owner's cookie.
        if (tokens.ownerOf(refreshToken).filter(userId::equals).isEmpty()) {
            throw new ApiException(ErrorCode.REFRESH_TOKEN_INVALID, "Refresh cookie is not the bearer's");
        }

        AuthenticatedSession session = tokens.rotate(refreshToken, request, users::findById,
                (tokenUserId, ignoredSessionWorkspace) -> Optional.of(target));

        selection.remember(session.user(), target);
        audit.event(AuthEventType.WORKSPACE_SWITCHED).actor(userId).workspace(workspaceId).from(request).record();
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
     * Pairs the extension in a new refresh-token family, so either session can be revoked alone.
     * Pairing again replaces the previous extension session: a minted token the extension never
     * received would otherwise stay live for its full TTL.
     */
    @Transactional
    public AuthenticatedSession pairExtension(UUID userId, UUID pairedWorkspaceId, HttpServletRequest request) {
        User user = requireUser(userId);
        tokens.revokeSessionsForClient(userId, SessionClient.BROWSER_EXTENSION, RevokeReason.SUPERSEDED);

        // Into the web session's workspace, which the extension's family then keeps: a web switch moves nothing.
        AuthenticatedSession paired = tokens.issue(user, selection.select(user, pairedWorkspaceId).orElse(null),
                request, SessionClient.BROWSER_EXTENSION);

        audit.event(AuthEventType.EXTENSION_PAIRED).actor(userId).from(request).record();
        return paired;
    }

    /**
     * The TTL and session label come from the client passed here, never from what the caller claims.
     * Exact, never falling through: a capture filed after a removal must not land in another firm.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthenticatedSession refreshExtension(String refreshToken, HttpServletRequest request) {
        return tokens.rotate(refreshToken, request, users::findById, selection::membershipIn,
                SessionClient.BROWSER_EXTENSION);
    }

    /** Falls through {@link WorkspaceSelection} once the session's membership ends — audited, since nobody asked. */
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
                    .target("WORKSPACE", sessionWorkspaceId).reason("MEMBERSHIP_ENDED").from(request)
                    .record();
        }
        return landed;
    }

    @Transactional(readOnly = true)
    public User requireUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.of(ErrorCode.INVALID_CREDENTIALS));
    }
}
