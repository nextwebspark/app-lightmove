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
 * A link between one of our users and an account at an external identity provider.
 *
 * <p>Keyed on {@code (provider, providerUserId)} rather than on the email address, because an email
 * address is not a stable identifier — people rename them, and providers let them. The provider's
 * subject id is the thing that actually persists.
 */
@Entity
@Table(name = "app_lm_user_identity")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserIdentity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AuthProvider provider;

    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    @Column
    private String email;

    @Column(name = "linked_at", nullable = false, updatable = false)
    private Instant linkedAt = Instant.now();

    public static UserIdentity link(UUID userId, AuthProvider provider, String providerUserId, String email) {
        UserIdentity identity = new UserIdentity();
        identity.userId = userId;
        identity.provider = provider;
        identity.providerUserId = providerUserId;
        identity.email = email;
        identity.linkedAt = Instant.now();
        return identity;
    }
}
