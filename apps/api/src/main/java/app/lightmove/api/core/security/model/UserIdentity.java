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
 * A link between one of our users and the account that proved who they are.
 *
 * <p>Keyed on {@code (provider, providerUserId)} rather than on the email address, because an email
 * address is not a stable identifier — people rename them, and providers let them. The provider's
 * subject id is the thing that actually persists.
 *
 * <p>{@code provider} is a plain string and deliberately not an enum: {@link #LOCAL_PROVIDER} means
 * an email plus a password we hashed ourselves, and every other value is the <i>uppercased OAuth
 * registration id</i> from configuration ({@code GOOGLE}, {@code LINKEDIN}, …). That is what makes
 * wiring up another identity provider a yml block and nothing else — an enum constant here would
 * drag a recompile and, before V24 dropped it, a schema migration along with it.
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
