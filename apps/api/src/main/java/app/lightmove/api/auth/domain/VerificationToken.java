package app.lightmove.api.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A single-use, time-limited token emailed to a user to prove they control an address —
 * for email verification or a password reset.
 *
 * <p>Stored as a SHA-256 hash. The plaintext exists only in the email we sent; if the database leaks,
 * the tokens in it cannot be redeemed.
 */
@Entity
@Table(name = "app_lm_verification_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationToken {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TokenPurpose purpose;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public static VerificationToken issue(UUID userId, String tokenHash, TokenPurpose purpose, Instant expiresAt) {
        VerificationToken token = new VerificationToken();
        token.userId = userId;
        token.tokenHash = tokenHash;
        token.purpose = purpose;
        token.expiresAt = expiresAt;
        token.createdAt = Instant.now();
        return token;
    }

    public boolean isRedeemable(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }

    public void consume(Instant now) {
        if (consumedAt != null) {
            throw new IllegalStateException("Verification token already consumed");
        }
        this.consumedAt = now;
    }
}
