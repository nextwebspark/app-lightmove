package app.lightmove.api.core.security.service;

import app.lightmove.api.core.audit.constant.AuthEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.LockoutSettings;
import app.lightmove.api.core.email.service.EmailSender;
import app.lightmove.api.core.email.service.EmailTemplates;
import app.lightmove.api.core.security.model.User;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * One failed sign-in step counted against the account — a wrong password, or a wrong code after a right one — so
 * both share the five-strike lock and its one email.
 */
@Component
@Slf4j
public class LoginLockout {

    private final AuditService audit;
    private final EmailSender emailSender;
    private final EmailTemplates templates;
    private final LockoutSettings config;

    public LoginLockout(AuditService audit, EmailSender emailSender, EmailTemplates templates,
                        LightMoveProperties properties) {
        this.audit = audit;
        this.emailSender = emailSender;
        this.templates = templates;
        this.config = properties.auth().lockout();
    }

    /** Records {@code failure} with the new count, and arms the lock once the count reaches the limit. */
    public void recordFailure(User user, Instant now, AuditService.Builder failure, HttpServletRequest request) {
        user.recordFailedLogin(now, config.maxFailedAttempts(), config.duration());
        failure.detail("failedAttempts", user.getFailedLoginAttempts()).record();

        if (user.isLocked(now)) {
            log.warn("Account {} locked after {} failed attempts", user.getId(), user.getFailedLoginAttempts());
            audit.event(AuthEventType.ACCOUNT_LOCKED).failed().actor(user.getId()).from(request)
                    .detail("until", String.valueOf(user.getLockedUntil())).record();

            // Here, not on every later refusal: one mail per lock window, not one per guess.
            emailSender.send(templates.buildAccountLockedEmail(
                    user.getEmail(), user.getFullName(), String.valueOf(user.getLockedUntil())));
        }
    }
}
