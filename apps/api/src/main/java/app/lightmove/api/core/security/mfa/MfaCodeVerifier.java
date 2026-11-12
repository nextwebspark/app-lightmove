package app.lightmove.api.core.security.mfa;

import app.lightmove.api.core.crypto.service.SecretCipher;
import app.lightmove.api.core.security.token.Tokens;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Whether a typed code proves the factor: six digits from the app, or, on an enabled factor, one recovery code,
 * spent as it passes. The caller holds the factor's row lock, so the replay guard's step is written once.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MfaCodeVerifier {

    private final SecretCipher cipher;
    private final MfaRecoveryCodeRepository recoveryCodes;

    public Optional<MfaCodeKind> verify(MfaFactor factor, String typed, Instant now) {
        String digits = typed == null ? "" : typed.replaceAll("\\s", "");
        if (digits.length() == 6 && digits.chars().allMatch(Character::isDigit)) {
            return verifyAuthenticatorCode(factor, digits, now);
        }

        String recoveryCode = RecoveryCodes.normalise(typed);
        if (factor.isEnabled() && RecoveryCodes.looksLikeOne(recoveryCode)
                && recoveryCodes.consume(factor.getUserId(), Tokens.hash(recoveryCode), now) == 1) {
            return Optional.of(MfaCodeKind.RECOVERY_CODE);
        }
        return Optional.empty();
    }

    private Optional<MfaCodeKind> verifyAuthenticatorCode(MfaFactor factor, String digits, Instant now) {
        String secret;
        try {
            secret = cipher.decrypt(factor.getSecretEncrypted(), factor.secretContext());
        } catch (RuntimeException e) {
            // A deployment that lost its keyset: the app's codes cannot be checked, a recovery code still can.
            log.error("The authenticator secret of user {} could not be read", factor.getUserId());
            return Optional.empty();
        }

        OptionalLong step = Totp.matchingStep(secret, digits, now, factor.getLastUsedStep());
        if (step.isEmpty()) {
            return Optional.empty();
        }
        factor.markUsed(step.getAsLong());
        return Optional.of(MfaCodeKind.AUTHENTICATOR);
    }
}
