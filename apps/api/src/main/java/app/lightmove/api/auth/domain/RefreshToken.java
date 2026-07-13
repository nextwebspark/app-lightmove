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
 * One issued refresh token, stored only as a SHA-256 hash.
 *
 * <p>Tokens rotate: redeeming one issues a successor and revokes the original, chaining them through
 * {@link #replacedById} inside a shared {@link #familyId}. A family is one login session — it begins
 * at sign-in and ends at logout, expiry, or theft.
 *
 * <p><b>Reuse detection.</b> A token that has already been rotated away should never be seen again.
 * If it is, one of two things happened: an attacker stole it and is redeeming it, or the attacker
 * stole and redeemed it already and this is the legitimate user arriving with a token that is now
 * stale. We cannot tell those apart, and in both cases someone unauthorised holds a valid credential.
 * So the entire family is revoked and both parties are forced to sign in again. Losing a session is
 * an acceptable price; leaving a thief with a live token is not.
 *
 * <p>Not a {@code BaseEntity}: a refresh token is never updated in place except to be revoked, and it
 * has no meaningful {@code updated_at} — it is an event, not a mutable record.
 */
@Entity
@Table(name = "app_lm_refresh_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoked_reason", length = 32)
    private RevokeReason revokedReason;

    @Column(name = "replaced_by_id")
    private UUID replacedById;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    // varchar(45), not inet — long enough for an IPv6 address, and matching the schema. The entity used
    // to claim `inet`, which Postgres will not implicitly cast a bound string to; it was inert only
    // because Hibernate never touches the DDL (ddl-auto: none), so the lie sat there waiting for someone
    // to turn generation on and be very confused.
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Opens a new family: this is a fresh login, not a continuation of an existing session. */
    public static RefreshToken issue(UUID userId, String tokenHash, Instant expiresAt,
                                     String userAgent, String ipAddress) {
        return issueInFamily(userId, tokenHash, UUID.randomUUID(), expiresAt, userAgent, ipAddress);
    }

    /** Continues an existing family: the same session, one rotation later. */
    public static RefreshToken issueInFamily(UUID userId, String tokenHash, UUID familyId, Instant expiresAt,
                                             String userAgent, String ipAddress) {
        RefreshToken token = new RefreshToken();
        token.userId = userId;
        token.tokenHash = tokenHash;
        token.familyId = familyId;
        token.expiresAt = expiresAt;
        token.userAgent = userAgent;
        token.ipAddress = ipAddress;
        token.createdAt = Instant.now();
        return token;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }

    /** Usable exactly once, and only while alive. */
    public boolean isRedeemable(Instant now) {
        return !isRevoked() && !isExpired(now);
    }

    public void revoke(RevokeReason reason, Instant now) {
        if (isRevoked()) {
            return; // Idempotent: revoking a whole family will walk over tokens already dead.
        }
        this.revokedAt = now;
        this.revokedReason = reason;
    }

    /** Marks this token as spent and records which token superseded it. */
    public void rotateTo(RefreshToken successor, Instant now) {
        revoke(RevokeReason.ROTATED, now);
        this.replacedById = successor.getId();
    }
}
