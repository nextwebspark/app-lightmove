package app.lightmove.api.outreach.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.outreach.constant.MailboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One consultant's own mailbox in one workspace (V99), held as the mail service's grant id only. */
@Entity
@Table(name = "app_lm_mailbox_connection")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MailboxConnection extends BaseEntity {

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Column(name = "address", nullable = false, length = 320)
    private String address;

    @Column(name = "grant_id", nullable = false, length = 128)
    private String grantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private MailboxStatus status;

    @Column(name = "daily_cap", nullable = false)
    private int dailyCap;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    public static MailboxConnection connected(UUID workspaceId, UUID userId, GrantedMailbox mailbox, int dailyCap,
                                              Instant now) {
        MailboxConnection connection = new MailboxConnection();
        connection.workspaceId = workspaceId;
        connection.userId = userId;
        connection.dailyCap = dailyCap;
        connection.reconnect(mailbox, now);
        return connection;
    }

    public void reconnect(GrantedMailbox mailbox, Instant now) {
        this.provider = mailbox.provider();
        this.address = mailbox.address();
        this.grantId = mailbox.grantId();
        this.status = MailboxStatus.ACTIVE;
        this.connectedAt = now;
    }

    public void markAccessWithdrawn() {
        this.status = MailboxStatus.ERROR;
    }

    public boolean canSend() {
        return status == MailboxStatus.ACTIVE;
    }
}
