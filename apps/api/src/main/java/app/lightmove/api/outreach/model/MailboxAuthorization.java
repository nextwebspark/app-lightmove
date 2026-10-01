package app.lightmove.api.outreach.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A mailbox connection started and not yet back from the provider's consent screen (V99). It is what
 * ties the callback, which carries no bearer token, to the consultant who asked; the state is stored
 * hashed and is redeemed once.
 */
@Entity
@Table(name = "app_lm_mailbox_authorization")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MailboxAuthorization extends BaseEntity {

    @Column(name = "state_hash", nullable = false, updatable = false, length = 64)
    private String stateHash;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "provider", nullable = false, updatable = false, length = 32)
    private String provider;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    public static MailboxAuthorization started(String stateHash, UUID workspaceId, UUID userId, String provider,
                                               Instant expiresAt) {
        MailboxAuthorization authorization = new MailboxAuthorization();
        authorization.stateHash = stateHash;
        authorization.workspaceId = workspaceId;
        authorization.userId = userId;
        authorization.provider = provider;
        authorization.expiresAt = expiresAt;
        return authorization;
    }

    public boolean hasExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
