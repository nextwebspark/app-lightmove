package app.lightmove.api.core.security.apikey;

import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A bearer key for the public API (V114), held only as the SHA-256 of its secret. */
@Entity
@Table(name = "app_lm_api_key")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiKey extends BaseEntity {

    public static final int MAX_NAME = 80;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16, updatable = false)
    private ApiKeyKind kind;

    /** Set exactly on a {@link ApiKeyKind#PERSONAL} key: whose access it borrows. */
    @Column(name = "owner_user_id", updatable = false)
    private UUID ownerUserId;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "name", nullable = false, length = MAX_NAME)
    private String name;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "token_hint", nullable = false, length = 32, updatable = false)
    private String tokenHint;

    /** {@link ApiKeyScope} wire tokens, in the enum's order. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scopes", nullable = false, updatable = false)
    private List<String> scopes = new ArrayList<>();

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "last_used_ip", length = 45)
    private String lastUsedIp;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by")
    private UUID revokedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoked_reason", length = 32)
    private ApiKeyRevokeReason revokedReason;

    public static ApiKey issued(UUID workspaceId, ApiKeyKind kind, UUID createdBy, String name, MintedApiKey minted,
                                List<ApiKeyScope> scopes, Instant expiresAt) {
        ApiKey key = new ApiKey();
        key.workspaceId = workspaceId;
        key.kind = kind;
        key.ownerUserId = kind == ApiKeyKind.PERSONAL ? createdBy : null;
        key.createdBy = createdBy;
        key.name = name;
        key.tokenHash = minted.hash();
        key.tokenHint = minted.hint();
        key.scopes = new ArrayList<>(scopes.stream().map(ApiKeyScope::value).toList());
        key.expiresAt = expiresAt;
        return key;
    }

    public ApiKeyStatus statusAt(Instant now) {
        if (revokedAt != null) {
            return ApiKeyStatus.REVOKED;
        }
        return expiresAt.isAfter(now) ? ApiKeyStatus.ACTIVE : ApiKeyStatus.EXPIRED;
    }

    public boolean isOwnedBy(UUID userId) {
        return kind == ApiKeyKind.PERSONAL && ownerUserId.equals(userId);
    }

    /** @return whether this call revoked it; a key already revoked keeps its first reason. */
    public boolean revoke(UUID revokedBy, ApiKeyRevokeReason reason, Instant now) {
        if (revokedAt != null) {
            return false;
        }
        this.revokedAt = now;
        this.revokedBy = revokedBy;
        this.revokedReason = reason;
        return true;
    }
}
