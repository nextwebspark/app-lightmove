package app.lightmove.api.core.security.mfa;

import app.lightmove.api.core.crypto.model.EncryptionContext;
import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A person's authenticator-app factor (V162), the shared secret in ciphertext bound to the user. The person's, not a
 * membership's: sign-in asks for it before any workspace is chosen. Pending until a first code confirms the app holds it.
 */
@Entity
@Table(name = "app_lm_user_mfa_factor")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MfaFactor extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "secret_encrypted", nullable = false)
    private String secretEncrypted;

    @Column(name = "enabled_at")
    private Instant enabledAt;

    @Column(name = "last_used_step")
    private Long lastUsedStep;

    public static MfaFactor pending(UUID userId, String secretEncrypted) {
        MfaFactor factor = new MfaFactor();
        factor.userId = userId;
        factor.restart(secretEncrypted);
        return factor;
    }

    public static EncryptionContext secretContext(UUID userId) {
        return EncryptionContext.ofUser(userId, "mfa-totp-secret");
    }

    public EncryptionContext secretContext() {
        return secretContext(userId);
    }

    /** A new setup replaces a pending one's secret; an enabled factor is turned off before it is set up again. */
    public void restart(String secretEncrypted) {
        if (isEnabled()) {
            throw new IllegalStateException("An enabled factor is not set up again");
        }
        this.secretEncrypted = Objects.requireNonNull(secretEncrypted, "secretEncrypted");
        this.lastUsedStep = null;
    }

    public boolean isEnabled() {
        return enabledAt != null;
    }

    /** After a first code passed {@link MfaCodeVerifier}, which already recorded its step. */
    public void enable(Instant now) {
        this.enabledAt = now;
    }

    public void markUsed(long step) {
        this.lastUsedStep = step;
    }
}
