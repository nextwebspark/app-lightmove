package app.lightmove.api.core.security.service;

import app.lightmove.api.core.audit.constant.AuthEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.email.service.EmailSender;
import app.lightmove.api.core.email.service.EmailTemplates;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.AuthenticatedSession;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.token.RevokeReason;
import app.lightmove.api.core.security.token.TokenService;
import app.lightmove.api.workspace.model.WorkspaceMember;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settings → Security: changing a password you already know.
 *
 * <p>The sibling of {@link PasswordResetService}, not a variant of it. A reset is anchored to an
 * emailed token that also proves the mailbox, clears a lockout and verifies the address; this is
 * anchored to the current password and does none of those. Sharing an implementation would mean one
 * of the two carrying the other's decisions.
 *
 * <p>What they do share is the ending, and it is deliberate: every other session dies, and the caller
 * is handed a fresh one so that changing a password does not sign you out of the tab you did it in.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordChangeService {

    private final PasswordPolicy passwords;
    private final TokenService tokens;
    private final AuthenticationService authentication;
    private final EmailSender emailSender;
    private final EmailTemplates templates;
    private final AuditService audit;
    private final LightMoveProperties properties;

    /**
     * Every refusal is thrown before the hash is touched, so a rejected attempt leaves the account
     * exactly as it was — no {@code noRollbackFor} needed, unlike {@code login()}.
     */
    @Transactional
    public AuthenticatedSession change(UUID userId, String currentPassword, String newPassword,
                                       HttpServletRequest request) {
        User user = authentication.requireUser(userId);

        // This endpoint mints a session, so it carries the same status gate as login, refresh, OAuth and
        // reset. Without it a suspended user holding a still-valid access token could change their
        // password to mint a fresh one, and repeat — turning the ≤15-minute window that suspension is
        // documented to leave open into an unbounded one.
        if (!user.getStatus().canAuthenticate()) {
            audit.event(AuthEventType.PASSWORD_CHANGED).failed().actor(userId).from(request)
                    .reason("status_" + user.getStatus()).record();
            throw ApiException.of(ErrorCode.ACCOUNT_SUSPENDED);
        }

        if (!user.hasPassword()) {
            throw ApiException.of(ErrorCode.PASSWORD_NOT_SET);
        }

        if (!passwords.matches(currentPassword, user.getPasswordHash())) {
            audit.event(AuthEventType.PASSWORD_CHANGED).failed().actor(userId).from(request)
                    .reason("current_password_mismatch").record();
            throw ApiException.withField(ErrorCode.CURRENT_PASSWORD_INVALID,
                    "currentPassword", ErrorCode.CURRENT_PASSWORD_INVALID.defaultMessage());
        }

        String passwordProblem = passwords.validate(newPassword);
        if (passwordProblem != null) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "newPassword", passwordProblem);
        }

        if (passwords.matches(newPassword, user.getPasswordHash())) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "newPassword",
                    "Choose a password different from your current one");
        }

        user.changePassword(passwords.hash(newPassword));

        // Before issuing, so the session handed back below survives the revocation it triggered.
        tokens.revokeAllSessions(userId, RevokeReason.PASSWORD_CHANGED);

        log.info("Password changed for user {}", userId);
        audit.event(AuthEventType.PASSWORD_CHANGED).actor(userId).from(request).record();

        emailSender.send(templates.buildPasswordChangedEmail(
                user.getEmail(), user.getFullName(), properties.web().baseUrl() + "/forgot-password"));

        WorkspaceMember membership = authentication.activeMembership(userId).orElse(null);
        return tokens.issue(user, membership, request);
    }
}
