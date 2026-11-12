package app.lightmove.api.outreach.model;

import app.lightmove.api.core.crypto.model.EncryptionContext;
import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.outreach.constant.ZoomConnectionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One consultant's own Zoom account in one workspace (V109), held as Zoom's refresh token in ciphertext bound to this
 * workspace and consultant.
 */
@Entity
@Table(name = "app_lm_zoom_connection")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ZoomConnection extends BaseEntity {

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "zoom_user_id", nullable = false, length = 64)
    private String zoomUserId;

    @Column(name = "refresh_token_encrypted", nullable = false)
    private String refreshTokenEncrypted;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ZoomConnectionStatus status;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    /** The OAuth client this account was connected through (V168); null when connected before V168. */
    @Column(name = "app_client_id")
    private String appClientId;

    public static ZoomConnection connected(UUID workspaceId, UUID userId, String zoomUserId,
                                           String refreshTokenEncrypted, String appClientId, Instant now) {
        ZoomConnection connection = new ZoomConnection();
        connection.workspaceId = workspaceId;
        connection.userId = userId;
        connection.reconnect(zoomUserId, refreshTokenEncrypted, appClientId, now);
        return connection;
    }

    public void reconnect(String zoomUserId, String refreshTokenEncrypted, String appClientId, Instant now) {
        this.zoomUserId = Objects.requireNonNull(zoomUserId, "zoomUserId");
        this.refreshTokenEncrypted = Objects.requireNonNull(refreshTokenEncrypted, "refreshTokenEncrypted");
        this.appClientId = Objects.requireNonNull(appClientId, "appClientId");
        this.status = ZoomConnectionStatus.ACTIVE;
        this.connectedAt = now;
    }

    public static EncryptionContext refreshTokenContext(UUID workspaceId, UUID userId) {
        return new EncryptionContext(workspaceId, "zoom-refresh-token:" + userId);
    }

    public EncryptionContext refreshTokenContext() {
        return refreshTokenContext(workspaceId, userId);
    }

    /** Zoom rotates the refresh token on every use; the newest is the one kept. */
    public void rotateRefreshToken(String refreshTokenEncrypted) {
        this.refreshTokenEncrypted = Objects.requireNonNull(refreshTokenEncrypted, "refreshTokenEncrypted");
    }

    public void markAccessWithdrawn() {
        this.status = ZoomConnectionStatus.ERROR;
    }

    public boolean canUse() {
        return status == ZoomConnectionStatus.ACTIVE;
    }

    /** Its refresh token was issued to another client than {@code clientId}, so that app cannot refresh it. */
    public boolean isFromAnotherApp(String clientId) {
        return appClientId != null && !appClientId.equals(clientId);
    }

    /** A connection from before V168 learns its app from the first refresh that app answers. */
    public void recordAppClient(String clientId) {
        if (appClientId == null) {
            this.appClientId = Objects.requireNonNull(clientId, "clientId");
        }
    }

    /** As {@link MailboxConnection#isLiveThrough}: a connection from before V168 is taken to be the app until now's. */
    public boolean isLiveThrough(String clientId) {
        return canUse() && (appClientId == null || appClientId.equals(clientId));
    }
}
