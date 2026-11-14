package app.lightmove.api.core.security.service;

import app.lightmove.api.core.audit.constant.AuthEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.constant.PrivacyPolicy;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A signed-in person accepting the terms and privacy policy again once their version changes (#928). The account's
 * own event, written with no workspace, as a password change is.
 */
@Service
@RequiredArgsConstructor
public class TermsAcceptanceService {

    private final UserRepository users;
    private final AuditService audit;
    private final Clock clock;

    /** A version that is no longer current was read on a sheet opened before a deploy: they must read the new one. */
    @Transactional
    public void accept(UUID userId, String version, HttpServletRequest request) {
        if (!PrivacyPolicy.CURRENT_VERSION.equals(version)) {
            throw new ApiException(ErrorCode.TERMS_VERSION_OUTDATED,
                    "Accepted version " + version + " is not " + PrivacyPolicy.CURRENT_VERSION);
        }
        User user = users.findById(userId).orElseThrow(() -> ApiException.of(ErrorCode.INVALID_CREDENTIALS));
        if (!user.owesTermsAcceptance(version)) {
            return;
        }
        user.acceptTerms(version, clock.instant());
        audit.event(AuthEventType.USER_TERMS_ACCEPTED).actor(userId).from(request)
                .detail("version", version).record();
    }
}
