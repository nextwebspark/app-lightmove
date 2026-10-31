package app.lightmove.api.core.security.mfa;

import app.lightmove.api.core.audit.constant.AuthEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.MfaSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.AuthenticatedSession;
import app.lightmove.api.core.security.model.MfaChallengeIssued;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.core.security.service.LoginLockout;
import app.lightmove.api.core.security.service.WorkspaceSelection;
import app.lightmove.api.core.security.token.TokenService;
import app.lightmove.api.core.security.token.Tokens;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The step between a right password and a session. A wrong code counts against the account exactly as a wrong
 * password does, and the lock is cleared only here, on a right one: clearing it at the password would hand anyone
 * who has the password unlimited guesses at the code, five at a time.
 */
@Service
public class MfaChallengeService {

    private final MfaChallengeRepository challenges;
    private final MfaFactorRepository factors;
    private final MfaCodeVerifier codes;
    private final MfaRecoveryCodeRepository recoveryCodes;
    private final UserRepository users;
    private final LoginLockout lockout;
    private final TokenService tokens;
    private final WorkspaceSelection selection;
    private final AuditService audit;
    private final MfaSettings config;

    public MfaChallengeService(MfaChallengeRepository challenges, MfaFactorRepository factors,
                               MfaCodeVerifier codes, MfaRecoveryCodeRepository recoveryCodes,
                               UserRepository users, LoginLockout lockout, TokenService tokens,
                               WorkspaceSelection selection, AuditService audit, LightMoveProperties properties) {
        this.challenges = challenges;
        this.factors = factors;
        this.codes = codes;
        this.recoveryCodes = recoveryCodes;
        this.users = users;
        this.lockout = lockout;
        this.tokens = tokens;
        this.selection = selection;
        this.audit = audit;
        this.config = properties.auth().mfa();
    }

    /** Whether a proved password or mailbox still owes a code: an enabled factor, whatever the switch says now. */
    @Transactional(readOnly = true)
    public boolean owesCode(UUID userId) {
        return factors.existsEnabledForUser(userId);
    }

    @Transactional
    public MfaChallengeIssued issue(User user, Instant now) {
        challenges.deleteByUserId(user.getId());

        String plaintext = Tokens.generate();
        challenges.save(MfaChallenge.issue(
                user.getId(), Tokens.hash(plaintext), now, now.plus(config.challengeTtl())));
        return new MfaChallengeIssued(plaintext, config.challengeTtl());
    }

    /** Turning the factor off ends any challenge a right password earned meanwhile. */
    @Transactional
    public void clearFor(UUID userId) {
        challenges.deleteByUserId(userId);
    }

    /**
     * Every refusal is {@link ErrorCode#MFA_CODE_INVALID}, whatever its reason. <b>{@code noRollbackFor}:</b>
     * otherwise the failed-attempt count rolls back with the refusal and the lock never arms, as on login.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthenticatedSession redeem(String challengeToken, String code, HttpServletRequest request) {
        Instant now = Instant.now();

        if (challengeToken == null || challengeToken.isBlank()) {
            throw refuse(null, "no_challenge", request);
        }
        MfaChallenge challenge = challenges.findForUpdateByTokenHash(Tokens.hash(challengeToken))
                .orElseThrow(() -> refuse(null, "unknown_challenge", request));
        if (!challenge.isRedeemable(now)) {
            throw refuse(challenge.getUserId(), "challenge_expired", request);
        }

        User user = users.findById(challenge.getUserId())
                .orElseThrow(() -> refuse(null, "missing_user", request));
        if (user.isLocked(now)) {
            throw refuse(user.getId(), "account_locked", request);
        }
        if (!user.getStatus().canAuthenticate()) {
            throw refuse(user.getId(), "status_" + user.getStatus(), request);
        }

        Optional<MfaFactor> factor = factors.findForUpdateByUserId(user.getId()).filter(MfaFactor::isEnabled);
        if (factor.isEmpty()) {
            throw refuse(user.getId(), "no_factor", request);
        }

        Optional<MfaCodeKind> passed = codes.verify(factor.get(), code, now);
        if (passed.isEmpty()) {
            lockout.recordFailure(user, now, audit.event(AuthEventType.MFA_CHALLENGE_FAILED).failed()
                    .actor(user.getId()).from(request).reason("bad_code"), request);
            if (user.isLocked(now)) {
                challenges.delete(challenge);
            }
            throw ApiException.of(ErrorCode.MFA_CODE_INVALID);
        }

        challenge.consume(now);
        user.recordSuccessfulLogin(now);

        if (passed.get() == MfaCodeKind.RECOVERY_CODE) {
            audit.event(AuthEventType.MFA_RECOVERY_CODE_USED).actor(user.getId()).from(request)
                    .detail("left", recoveryCodes.countByUserIdAndUsedAtIsNull(user.getId())).record();
        }
        AuthenticatedSession session = tokens.issue(user, selection.signIn(user), request);
        audit.event(AuthEventType.LOGIN_SUCCEEDED).actor(user.getId()).workspace(session.workspaceId())
                .from(request).detail("secondFactor", passed.get().name()).record();
        return session;
    }

    private ApiException refuse(UUID userId, String reason, HttpServletRequest request) {
        AuditService.Builder failure = audit.event(AuthEventType.MFA_CHALLENGE_FAILED).failed().from(request)
                .reason(reason);
        if (userId != null) {
            failure.actor(userId);
        }
        failure.record();
        return ApiException.of(ErrorCode.MFA_CODE_INVALID);
    }
}
