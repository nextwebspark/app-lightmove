package app.lightmove.api.core.security.rbac;

import app.lightmove.api.core.audit.constant.AuthEventType;
import app.lightmove.api.core.audit.constant.PlatformEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.PlatformSecuritySettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.mfa.MfaCodeKind;
import app.lightmove.api.core.security.mfa.MfaCodeVerifier;
import app.lightmove.api.core.security.mfa.MfaFactor;
import app.lightmove.api.core.security.mfa.MfaFactorRepository;
import app.lightmove.api.core.security.mfa.MfaRecoveryCodeRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the platform routes ask of a super admin beyond the role (#1313): an enrolled factor on every route, and a
 * code entered within the step-up window on the high-impact ones. Both are off with
 * {@code lightmove.platform.require-two-factor}. Kept in the database, never in the token, so every instance agrees.
 */
@Service
public class PlatformTwoFactor {

    private final MfaFactorRepository factors;
    private final MfaCodeVerifier codes;
    private final MfaRecoveryCodeRepository recoveryCodes;
    private final PlatformStepUpStore stepUps;
    private final AuditService audit;
    private final PlatformSecuritySettings config;
    private final Clock clock;

    public PlatformTwoFactor(MfaFactorRepository factors, MfaCodeVerifier codes,
                             MfaRecoveryCodeRepository recoveryCodes, PlatformStepUpStore stepUps,
                             AuditService audit, LightMoveProperties properties, Clock clock) {
        this.factors = factors;
        this.codes = codes;
        this.recoveryCodes = recoveryCodes;
        this.stepUps = stepUps;
        this.audit = audit;
        this.config = properties.platform();
        this.clock = clock;
    }

    public void requireEnrolled(UUID userId) {
        if (config.requireTwoFactor() && enabledFactorOf(userId).isEmpty()) {
            throw ApiException.of(ErrorCode.PLATFORM_TWO_FACTOR_REQUIRED);
        }
    }

    /**
     * The factor and a fresh code, in one read of the factor. A code older than the factor it was entered for does
     * not count: turning two-factor off and on again must not carry an earlier code's window across.
     */
    public void requireRecentCode(UUID userId) {
        if (!config.requireTwoFactor()) {
            return;
        }
        MfaFactor factor = enabledFactorOf(userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.PLATFORM_TWO_FACTOR_REQUIRED));
        Instant earliest = clock.instant().minus(config.stepUpWindow());
        boolean recent = stepUps.verifiedAt(userId)
                .filter(verifiedAt -> !verifiedAt.isBefore(earliest) && !verifiedAt.isBefore(factor.getEnabledAt()))
                .isPresent();
        if (!recent) {
            throw ApiException.of(ErrorCode.PLATFORM_STEP_UP_REQUIRED);
        }
    }

    /** A right code, from the app or a recovery code, opens the window. Under the factor's row lock, as sign-in is. */
    @Transactional
    public void confirm(UUID userId, String code, HttpServletRequest request) {
        MfaFactor factor = factors.findForUpdateByUserId(userId)
                .filter(MfaFactor::isEnabled)
                .orElseThrow(() -> ApiException.of(ErrorCode.MFA_NOT_ENABLED));

        Instant now = clock.instant();
        Optional<MfaCodeKind> passed = codes.verify(factor, code, now);
        if (passed.isEmpty()) {
            audit.event(PlatformEventType.PLATFORM_STEP_UP_FAILED).failed().actor(userId).from(request)
                    .reason("bad_code").record();
            throw ApiException.withField(ErrorCode.MFA_CODE_INVALID, "code",
                    ErrorCode.MFA_CODE_INVALID.defaultMessage());
        }

        stepUps.record(userId, now);
        if (passed.get() == MfaCodeKind.RECOVERY_CODE) {
            audit.event(AuthEventType.MFA_RECOVERY_CODE_USED).actor(userId).from(request)
                    .detail("left", recoveryCodes.countByUserIdAndUsedAtIsNull(userId)).record();
        }
        audit.event(PlatformEventType.PLATFORM_STEP_UP_PASSED).actor(userId).from(request)
                .detail("secondFactor", passed.get().name()).record();
    }

    private Optional<MfaFactor> enabledFactorOf(UUID userId) {
        return factors.findByUserId(userId).filter(MfaFactor::isEnabled);
    }
}
