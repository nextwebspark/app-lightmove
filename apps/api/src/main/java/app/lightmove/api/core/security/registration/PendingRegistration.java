package app.lightmove.api.core.security.registration;

import app.lightmove.api.core.security.model.ProviderIdentity;
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
 * A first sign-in with a provider, held until the person accepts the terms (#928): no account exists yet. Single use,
 * minutes long, stored as its SHA-256; the plaintext rides only the {@code lm_pending_registration} cookie.
 */
@Entity
@Table(name = "app_lm_pending_registration")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PendingRegistration {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "token_hash", nullable = false, updatable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "provider", nullable = false, updatable = false, length = 32)
    private String provider;

    @Column(name = "subject", nullable = false, updatable = false)
    private String subject;

    @Column(name = "email", nullable = false, updatable = false)
    private String email;

    @Column(name = "full_name", nullable = false, updatable = false, length = 160)
    private String fullName;

    @Column(name = "picture_url", updatable = false, length = 2048)
    private String pictureUrl;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static PendingRegistration hold(ProviderIdentity identity, String tokenHash, Instant now,
                                           Instant expiresAt) {
        PendingRegistration pending = new PendingRegistration();
        pending.tokenHash = tokenHash;
        pending.provider = identity.provider();
        pending.subject = identity.subject();
        pending.email = identity.email();
        pending.fullName = identity.fullName();
        pending.pictureUrl = identity.pictureUrl();
        pending.createdAt = now;
        pending.expiresAt = expiresAt;
        return pending;
    }

    public boolean isRedeemable(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }

    public void consume(Instant now) {
        if (consumedAt != null) {
            throw new IllegalStateException("Pending registration already consumed");
        }
        this.consumedAt = now;
    }

    public ProviderIdentity identity() {
        return new ProviderIdentity(provider, subject, email, fullName, pictureUrl);
    }
}
