package app.lightmove.api.outreach.model;

import app.lightmove.api.core.crypto.model.EncryptionContext;
import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.outreach.constant.IntegrationProvider;
import app.lightmove.api.outreach.constant.MailboxGatewayKind;
import app.lightmove.api.outreach.constant.MailboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One consultant's own mailbox in one workspace (V99). Held through Nylas as its grant id only, or through our own
 * gateway (V107) with the provider's refresh token as ciphertext bound to this workspace and consultant.
 */
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

    /** The booking link's path, {@code /book/<slug>} (V104): kept across a reconnect, so a sent link still works. */
    @Column(name = "booking_slug", length = 64)
    private String bookingSlug;

    /** The Scheduler configuration behind the link; a grant's, so a reconnect clears it. */
    @Column(name = "booking_configuration_id", length = 128)
    private String bookingConfigurationId;

    /** Which gateway made this connection (V107); every call for it goes there. */
    @Enumerated(EnumType.STRING)
    @Column(name = "gateway", nullable = false, length = 16)
    private MailboxGatewayKind gateway = MailboxGatewayKind.NYLAS;

    /** The provider's refresh token, encrypted under {@link #refreshTokenContext()}; a direct connection's only. */
    @Column(name = "refresh_token_encrypted")
    private String refreshTokenEncrypted;

    /** The Recall.ai calendar its events are read through, while its workspace syncs calendars through Recall. */
    @Column(name = "recall_calendar_id", length = 128)
    private String recallCalendarId;

    /** Failed creates of the Recall calendar since the mailbox connected; each waits longer for the next. */
    @Column(name = "recall_calendar_attempts", nullable = false)
    private int recallCalendarAttempts;

    @Column(name = "recall_calendar_retry_at")
    private Instant recallCalendarRetryAt;

    /** {@code refreshTokenEncrypted}: the refresh token under {@link #refreshTokenContext}; null for Nylas. */
    public static MailboxConnection connected(UUID workspaceId, UUID userId, GrantedMailbox mailbox,
                                              String refreshTokenEncrypted, int dailyCap, Instant now) {
        MailboxConnection connection = new MailboxConnection();
        connection.workspaceId = workspaceId;
        connection.userId = userId;
        connection.dailyCap = dailyCap;
        connection.reconnect(mailbox, refreshTokenEncrypted, now);
        return connection;
    }

    /** The Recall calendar is kept: a reconnect hands it the new refresh token rather than making another. */
    public void reconnect(GrantedMailbox mailbox, String refreshTokenEncrypted, Instant now) {
        MailboxGatewayKind kind = MailboxGatewayKind.ofGrant(mailbox.grantId());
        if ((kind == MailboxGatewayKind.DIRECT) != (refreshTokenEncrypted != null)) {
            throw new IllegalArgumentException("A direct connection holds a refresh token, and only a direct one");
        }
        this.gateway = kind;
        this.refreshTokenEncrypted = refreshTokenEncrypted;
        this.provider = mailbox.provider();
        this.address = mailbox.address();
        this.grantId = mailbox.grantId();
        this.status = MailboxStatus.ACTIVE;
        this.connectedAt = now;
        this.calendarSyncedAt = null;
        this.calendarSyncAttempts = 0;
        this.calendarSyncRetryAt = null;
        this.bookingConfigurationId = null;
        this.recallCalendarAttempts = 0;
        this.recallCalendarRetryAt = null;
    }

    public void claimBookingSlug(String slug) {
        this.bookingSlug = slug;
    }

    public void holdBookingPage(String configurationId) {
        this.bookingConfigurationId = configurationId;
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

    /** What the refresh token is encrypted under: this workspace, this consultant, this column. */
    public static EncryptionContext refreshTokenContext(UUID workspaceId, UUID userId) {
        return new EncryptionContext(workspaceId, "mailbox-refresh-token:" + userId);
    }

    public EncryptionContext refreshTokenContext() {
        return refreshTokenContext(workspaceId, userId);
    }

    /** The provider may hand out a new refresh token with an access token; the newest is the one kept. */
    public void rotateRefreshToken(String refreshTokenEncrypted) {
        if (!isDirect()) {
            throw new IllegalStateException("Only a direct connection holds a refresh token");
        }
        this.refreshTokenEncrypted = Objects.requireNonNull(refreshTokenEncrypted, "refreshTokenEncrypted");
    }

    public boolean isDirect() {
        return gateway == MailboxGatewayKind.DIRECT;
    }

    public IntegrationProvider integrationProvider() {
        return IntegrationProvider.valueOf(provider.toUpperCase(Locale.ROOT));
    }

    public void holdRecallCalendar(String calendarId) {
        this.recallCalendarId = Objects.requireNonNull(calendarId, "calendarId");
        this.recallCalendarAttempts = 0;
        this.recallCalendarRetryAt = null;
    }

    /** The Recall calendar could not be made; the poll tries again after {@code wait}. */
    public void markRecallCalendarFailed(Instant now, Duration wait) {
        this.recallCalendarAttempts = recallCalendarAttempts + 1;
        this.recallCalendarRetryAt = now.plus(wait);
    }

    /**
     * A Recall calendar keeps the host and the address it was made for, so a reconnect elsewhere must not patch
     * it: it is let go here, before the reconnect, and a new one is made for the new mailbox.
     *
     * @return the calendar to delete at Recall, or null when the reconnect is to the same mailbox
     */
    public String releaseRecallCalendarUnlessFor(GrantedMailbox mailbox) {
        boolean sameMailbox = Objects.equals(provider, mailbox.provider())
                && address != null && address.equalsIgnoreCase(mailbox.address());
        return sameMailbox ? null : releaseRecallCalendar();
    }

    /** Forgets the Recall calendar and answers its id, for the caller to delete at Recall once this commits. */
    public String releaseRecallCalendar() {
        String released = recallCalendarId;
        this.recallCalendarId = null;
        return released;
    }

    public boolean canSend() {
        return status == MailboxStatus.ACTIVE;
    }

    public ZoneId zone() {
        return ZoneId.of(timeZone);
    }
}
