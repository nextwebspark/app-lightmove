package app.lightmove.api.core.security.model;

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
 * Links a user to the account that proved who they are, keyed on {@code (provider, providerUserId)}
 * since emails change. {@code provider} is a string, not an enum — {@link #LOCAL_PROVIDER} or the
 * uppercased OAuth registration id — so another identity provider is a yml block and nothing else.
 */
@Entity
@Table(name = "app_lm_user_identity")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserIdentity {

    /** Not a provider at all: the password we hold ourselves. */
    public static final String LOCAL_PROVIDER = "LOCAL";

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    @Column
    private String email;

    @Column(name = "linked_at", nullable = false, updatable = false)
    private Instant linkedAt = Instant.now();

    public static UserIdentity link(UUID userId, String provider, String providerUserId, String email) {
        UserIdentity identity = new UserIdentity();
        identity.userId = userId;
        identity.provider = provider;
        identity.providerUserId = providerUserId;
        identity.email = email;
        identity.linkedAt = Instant.now();
        return identity;
    }
}
