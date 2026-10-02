package app.lightmove.api.outreach.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.outreach.constant.MailboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
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

    /** The UAE's: the GCC working week is the default until a consultant elsewhere says otherwise. */
    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Dubai");

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

    /** The sender's zone, which the sending window and the daily cap are read in (V101). */
    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone = DEFAULT_ZONE.getId();

    /** When the calendar was last read whole (V102); null while that read is still owed. */
    @Column(name = "calendar_synced_at")
    private Instant calendarSyncedAt;

    /** Failed reads of the calendar since it was connected (V103); each one waits longer for the next. */
    @Column(name = "calendar_sync_attempts", nullable = false)
    private int calendarSyncAttempts;

    @Column(name = "calendar_sync_retry_at")
    private Instant calendarSyncRetryAt;

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
        this.calendarSyncedAt = null;
        this.calendarSyncAttempts = 0;
        this.calendarSyncRetryAt = null;
    }

    public void markCalendarSynced(Instant now) {
        this.calendarSyncedAt = now;
        this.calendarSyncRetryAt = null;
    }

    /** The calendar could not be read; the next try waits {@code wait}. */
    public void markCalendarSyncFailed(Instant now, Duration wait) {
        this.calendarSyncAttempts = calendarSyncAttempts + 1;
        this.calendarSyncRetryAt = now.plus(wait);
    }

    public void markAccessWithdrawn() {
        this.status = MailboxStatus.ERROR;
    }

    public boolean canSend() {
        return status == MailboxStatus.ACTIVE;
    }

    public ZoneId zone() {
        return ZoneId.of(timeZone);
    }
}
