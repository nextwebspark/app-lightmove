package app.lightmove.api.core.security.mfa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * What a correct password earns a person with a factor, in place of a session: single use, a few minutes long,
 * stored as its SHA-256. The plaintext rides only the {@code lm_mfa_challenge} cookie.
 */
@Entity
@Table(name = "app_lm_mfa_challenge")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MfaChallenge {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, updatable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static MfaChallenge issue(UUID userId, String tokenHash, Instant now, Instant expiresAt) {
        MfaChallenge challenge = new MfaChallenge();
        challenge.userId = userId;
        challenge.tokenHash = tokenHash;
        challenge.createdAt = now;
        challenge.expiresAt = expiresAt;
        return challenge;
    }

    public boolean isRedeemable(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }

    public void consume(Instant now) {
        if (consumedAt != null) {
            throw new IllegalStateException("Challenge already consumed");
        }
        this.consumedAt = now;
    }
}
