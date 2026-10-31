package app.lightmove.api.core.security.mfa;

import app.lightmove.api.core.audit.constant.AuthEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.MfaSettings;
import app.lightmove.api.core.crypto.service.SecretCipher;
import app.lightmove.api.core.email.service.EmailSender;
import app.lightmove.api.core.email.service.EmailTemplates;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.dto.MfaSetupResponse;
import app.lightmove.api.core.security.dto.MfaStatusResponse;
import app.lightmove.api.core.security.dto.RecoveryCodesResponse;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.service.AuthenticationService;
import app.lightmove.api.core.security.service.CurrentPasswordCheck;
import app.lightmove.api.core.security.token.ActiveSessionService;
import app.lightmove.api.core.security.token.RevokeReason;
import app.lightmove.api.core.security.token.Tokens;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settings → Security's two-factor card: set up, turn on, turn off, new recovery codes. Each change asks for the
 * current password, where the account has one, and a code, and ends every other session of the person's, as a
 * password change does.
 */
@Service
@Slf4j
public class MfaService {

    private static final String ISSUER = "Uncava";

    private final AuthenticationService authentication;
    private final CurrentPasswordCheck currentPasswordCheck;
    private final MfaFactorRepository factors;
    private final MfaRecoveryCodeRepository recoveryCodes;
    private final MfaChallengeService challenges;
    private final MfaCodeVerifier codes;
    private final SecretCipher cipher;
    private final ActiveSessionService sessions;
    private final AuditService audit;
    private final EmailSender emailSender;
    private final EmailTemplates templates;
    private final MfaSettings config;
    private final String webBaseUrl;

    public MfaService(AuthenticationService authentication, CurrentPasswordCheck currentPasswordCheck,
                      MfaFactorRepository factors, MfaRecoveryCodeRepository recoveryCodes,
                      MfaChallengeService challenges, MfaCodeVerifier codes, SecretCipher cipher,
                      ActiveSessionService sessions, AuditService audit, EmailSender emailSender,
                      EmailTemplates templates, LightMoveProperties properties) {
        this.authentication = authentication;
        this.currentPasswordCheck = currentPasswordCheck;
        this.factors = factors;
        this.recoveryCodes = recoveryCodes;
        this.challenges = challenges;
        this.codes = codes;
        this.cipher = cipher;
        this.sessions = sessions;
        this.audit = audit;
        this.emailSender = emailSender;
        this.templates = templates;
        this.config = properties.auth().mfa();
        this.webBaseUrl = properties.web().baseUrl();
    }

    /** Offered only where it is switched on and the secret can be kept encrypted. */
    public boolean isOffered() {
        return config.enabled() && cipher.isAvailable();
    }

    @Transactional(readOnly = true)
    public MfaStatusResponse status(UUID userId) {
        Optional<MfaFactor> enabled = factors.findByUserId(userId).filter(MfaFactor::isEnabled);
        return new MfaStatusResponse(
                isOffered(),
                enabled.isPresent(),
                enabled.map(MfaFactor::getEnabledAt).orElse(null),
                enabled.isPresent() ? recoveryCodes.countByUserIdAndUsedAtIsNull(userId) : 0);
    }

    /**
     * A fresh secret, answered once and kept encrypted until a first code confirms the app holds it. Asks for no
     * password: nothing changes for the person until {@link #enable}, which does.
     */
    @Transactional
    public MfaSetupResponse startSetup(UUID userId) {
        requireOffered();
        User user = authentication.requireUser(userId);

        String secret = Totp.newSecret();
        String sealed = cipher.encrypt(secret, MfaFactor.secretContext(userId));
        // Inserted before the lock: FOR UPDATE locks nothing while there is no row, so two first setups would
        // otherwise both insert and one fail on the user's unique key.
        factors.insertPendingIfMissing(userId, sealed);
        MfaFactor factor = factors.findForUpdateByUserId(userId).orElseThrow();
        if (factor.isEnabled()) {
            throw ApiException.of(ErrorCode.MFA_ALREADY_ENABLED);
        }
        factor.restart(sealed);

        return new MfaSetupResponse(secret, Totp.otpauthUri(ISSUER, user.getEmail(), secret));
    }

    @Transactional
    public RecoveryCodesResponse enable(UUID userId, String currentPassword, String code,
                                        String presentedRefreshToken, HttpServletRequest request) {
        requireOffered();
        User user = authentication.requireUser(userId);
        UUID keptFamilyId = sessions.requireCurrentFamily(userId, presentedRefreshToken);
        requireOwner(user, currentPassword, keptFamilyId, AuthEventType.MFA_ENABLED, request);

        MfaFactor factor = factors.findForUpdateByUserId(userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.MFA_SETUP_NOT_STARTED));
        if (factor.isEnabled()) {
            throw ApiException.of(ErrorCode.MFA_ALREADY_ENABLED);
        }

        Instant now = Instant.now();
        requireCode(factor, code, now, AuthEventType.MFA_ENABLED, request);

        factor.enable(now);
        List<String> issued = replaceRecoveryCodes(userId, now);
        sessions.endOtherSessions(userId, keptFamilyId, RevokeReason.MFA_CHANGED);

        log.info("Two-factor authentication turned on for user {}", userId);
        audit.event(AuthEventType.MFA_ENABLED).actor(userId).from(request).record();
        emailSender.send(templates.buildTwoFactorEnabledEmail(
                user.getEmail(), user.getFullName(), webBaseUrl + "/settings/security"));

        return new RecoveryCodesResponse(issued);
    }

    /** Never gated on {@link #isOffered}: a person who has it can always turn it off. */
    @Transactional
    public void disable(UUID userId, String currentPassword, String code, String presentedRefreshToken,
                        HttpServletRequest request) {
        User user = authentication.requireUser(userId);
        UUID keptFamilyId = sessions.requireCurrentFamily(userId, presentedRefreshToken);
        requirePasswordWhereHeld(user, currentPassword, AuthEventType.MFA_DISABLED, request);
        MfaFactor factor = requireEnabledFactor(userId);
        requireCode(factor, code, Instant.now(), AuthEventType.MFA_DISABLED, request);

        challenges.clearFor(userId);
        recoveryCodes.deleteByUserId(userId);
        factors.delete(factor);
        sessions.endOtherSessions(userId, keptFamilyId, RevokeReason.MFA_CHANGED);

        log.info("Two-factor authentication turned off for user {}", userId);
        audit.event(AuthEventType.MFA_DISABLED).actor(userId).from(request).record();
        emailSender.send(templates.buildTwoFactorDisabledEmail(
                user.getEmail(), user.getFullName(), webBaseUrl + "/forgot-password"));
    }

    /** The old set stops working at once, whichever of it was left. */
    @Transactional
    public RecoveryCodesResponse regenerateRecoveryCodes(UUID userId, String currentPassword, String code,
                                                         HttpServletRequest request) {
        User user = authentication.requireUser(userId);
        requirePasswordWhereHeld(user, currentPassword, AuthEventType.MFA_RECOVERY_CODES_REGENERATED, request);
        MfaFactor factor = requireEnabledFactor(userId);

        Instant now = Instant.now();
        requireCode(factor, code, now, AuthEventType.MFA_RECOVERY_CODES_REGENERATED, request);
        List<String> issued = replaceRecoveryCodes(userId, now);

        audit.event(AuthEventType.MFA_RECOVERY_CODES_REGENERATED).actor(userId).from(request).record();
        return new RecoveryCodesResponse(issued);
    }

    /**
     * The owner proves it is them before enrolling a phone: by the current password, or — an account with none —
     * by a sign-in moments ago, which a session stolen earlier cannot show.
     */
    private void requireOwner(User user, String currentPassword, UUID familyId, AuthEventType event,
                              HttpServletRequest request) {
        if (user.hasPassword()) {
            requirePasswordWhereHeld(user, currentPassword, event, request);
            return;
        }
        Instant signedInAt = sessions.signedInAt(familyId);
        if (signedInAt == null || signedInAt.isBefore(Instant.now().minus(config.recentSignIn()))) {
            audit.event(event).failed().actor(user.getId()).from(request).reason("sign_in_not_recent").record();
            throw ApiException.of(ErrorCode.MFA_SIGN_IN_AGAIN);
        }
    }

    /** An account with no password turns it off, or takes new codes, on the strength of the code alone. */
    private void requirePasswordWhereHeld(User user, String currentPassword, AuthEventType event,
                                          HttpServletRequest request) {
        if (!user.hasPassword()) {
            return;
        }
        if (currentPassword == null || currentPassword.isEmpty()) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "currentPassword", "Enter your current password");
        }
        currentPasswordCheck.require(user, currentPassword, event, request);
    }

    private List<String> replaceRecoveryCodes(UUID userId, Instant now) {
        recoveryCodes.deleteByUserId(userId);
        List<String> issued = RecoveryCodes.newSet();
        recoveryCodes.saveAll(issued.stream()
                .map(issuedCode -> MfaRecoveryCode.issue(
                        userId, Tokens.hash(RecoveryCodes.normalise(issuedCode)), now))
                .toList());
        return issued;
    }

    private void requireOffered() {
        if (!isOffered()) {
            throw ApiException.of(ErrorCode.MFA_UNAVAILABLE);
        }
    }

    private MfaFactor requireEnabledFactor(UUID userId) {
        return factors.findForUpdateByUserId(userId)
                .filter(MfaFactor::isEnabled)
                .orElseThrow(() -> ApiException.of(ErrorCode.MFA_NOT_ENABLED));
    }

    private void requireCode(MfaFactor factor, String code, Instant now, AuthEventType event,
                             HttpServletRequest request) {
        if (codes.verify(factor, code, now).isEmpty()) {
            audit.event(event).failed().actor(factor.getUserId()).from(request).reason("bad_code").record();
            throw ApiException.withField(ErrorCode.MFA_CODE_INVALID,
                    "code", ErrorCode.MFA_CODE_INVALID.defaultMessage());
        }
    }
}
