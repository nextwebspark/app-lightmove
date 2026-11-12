package app.lightmove.api.core.security.service;

import app.lightmove.api.core.security.mfa.MfaChallengeRepository;
import app.lightmove.api.core.security.mfa.MfaFactor;
import app.lightmove.api.core.security.mfa.MfaFactorRepository;
import app.lightmove.api.core.security.mfa.MfaRecoveryCodeRepository;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.oauth.OAuthGrantService;
import app.lightmove.api.core.security.token.RevokeReason;
import app.lightmove.api.core.security.token.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * An account nobody had verified may have been opened by someone else on its owner's address. Once a provider proves
 * the mailbox, whatever that claimant set — password, sessions, second factor — goes, or they keep a way in.
 */
@Component
@RequiredArgsConstructor
public class UnverifiedAccountWipe {

    private final TokenService tokens;
    private final MfaChallengeRepository challenges;
    private final MfaRecoveryCodeRepository recoveryCodes;
    private final MfaFactorRepository factors;
    private final OAuthGrantService oauthGrants;

    /** @return whether there was anything to wipe */
    public boolean wipe(User user, HttpServletRequest request) {
        Optional<MfaFactor> factor = factors.findByUserId(user.getId());
        boolean heldCredentials = user.hasPassword() || factor.isPresent();

        user.discardPassword();
        tokens.revokeAllSessions(user.getId(), RevokeReason.PASSWORD_CHANGED);
        challenges.deleteByUserId(user.getId());
        recoveryCodes.deleteByUserId(user.getId());
        factor.ifPresent(factors::delete);
        oauthGrants.revokeAllOfUser(user.getId(), user.getId(), request);

        return heldCredentials;
    }
}
