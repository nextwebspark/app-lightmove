package app.lightmove.api.core.security.mfa;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.MfaSettings;
import app.lightmove.api.core.crypto.service.SecretCipher;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * A workspace's rule that its members hold a factor (V163). Read from the database on every check, never from a
 * token: a policy switched on reaches every live session at its next request, as the guard beans do.
 */
@Component
public class MfaPolicy {

    private final MfaFactorRepository factors;
    private final SecretCipher cipher;
    private final MfaSettings config;

    public MfaPolicy(MfaFactorRepository factors, SecretCipher cipher, LightMoveProperties properties) {
        this.factors = factors;
        this.cipher = cipher;
        this.config = properties.auth().mfa();
    }

    /** Offered only where it is switched on and the secret can be kept encrypted. */
    public boolean isOffered() {
        return config.enabled() && cipher.isAvailable();
    }

    /**
     * Whether this person must set up a factor before reading this workspace. Never where the feature is not
     * offered: a deployment that switched it off would otherwise hold everyone at a screen they cannot finish.
     */
    @Transactional(readOnly = true)
    public boolean owesEnrolment(UUID userId, UUID workspaceId) {
        return workspaceId != null && isOffered() && factors.workspaceRequiresAndUserLacks(userId, workspaceId);
    }

    @Transactional(readOnly = true)
    public boolean hasFactor(UUID userId) {
        return factors.existsEnabledForUser(userId);
    }

    /** When each of these people turned their factor on; someone without one is absent. */
    @Transactional(readOnly = true)
    public Map<UUID, Instant> enabledSince(Collection<UUID> userIds) {
        return factors.findByUserIdInAndEnabledAtIsNotNull(userIds).stream()
                .collect(Collectors.toMap(MfaFactor::getUserId, MfaFactor::getEnabledAt));
    }

    /** The workspaces this person is a member of that require a factor, by name. */
    @Transactional(readOnly = true)
    public List<String> workspacesRequiring(UUID userId) {
        return factors.namesOfWorkspacesRequiring(userId);
    }
}
