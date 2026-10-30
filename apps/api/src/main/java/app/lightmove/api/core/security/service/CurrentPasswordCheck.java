package app.lightmove.api.core.security.service;

import app.lightmove.api.core.audit.constant.AuthEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The current password a signed-in owner gives to change how they sign in — asked plainly, since the caller is
 * already who they say. Each refusal is recorded as a failed {@code event}.
 */
@Component
@RequiredArgsConstructor
public class CurrentPasswordCheck {

    private final PasswordPolicy passwords;
    private final AuditService audit;

    public void require(User user, String currentPassword, AuthEventType event, HttpServletRequest request) {
        if (!user.getStatus().canAuthenticate()) {
            audit.event(event).failed().actor(user.getId()).from(request)
                    .reason("status_" + user.getStatus()).record();
            throw ApiException.of(ErrorCode.ACCOUNT_SUSPENDED);
        }

        if (!user.hasPassword()) {
            throw ApiException.of(ErrorCode.PASSWORD_NOT_SET);
        }

        if (!passwords.matches(currentPassword, user.getPasswordHash())) {
            audit.event(event).failed().actor(user.getId()).from(request)
                    .reason("current_password_mismatch").record();
            throw ApiException.withField(ErrorCode.CURRENT_PASSWORD_INVALID,
                    "currentPassword", ErrorCode.CURRENT_PASSWORD_INVALID.defaultMessage());
        }
    }
}
