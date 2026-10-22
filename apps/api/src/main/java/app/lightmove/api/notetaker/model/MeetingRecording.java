package app.lightmove.api.notetaker.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.notetaker.constant.MeetingRecorderKind;
import app.lightmove.api.notetaker.constant.NotetakerEndReason;
import app.lightmove.api.notetaker.constant.NotetakerPlatform;
import app.lightmove.api.notetaker.constant.NotetakerStatus;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One Notetaker bot on one call (V149). Inserted by {@code MeetingRecordingClaims}, whose unique call key is the
 * claim; every later step is a method here. The run token's hash is cleared once the bot can change nothing more.
 */
@Entity
@Table(name = "app_lm_meeting_recording")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingRecording extends BaseEntity {

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "call_key", nullable = false, updatable = false, length = 64)
    private String callKey;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "person_id")
    private UUID personId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "mailbox_connection_id")
    private UUID mailboxConnectionId;

    @Column(name = "provider_event_id", nullable = false, updatable = false)
    private String providerEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, updatable = false, length = 16)
    private NotetakerPlatform platform;

    @Column(name = "join_url", nullable = false, updatable = false)
    private String joinUrl;

    @Column(name = "title")
    private String title;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "recorder", nullable = false, length = 8)
    private MeetingRecorderKind recorder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotetakerStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 32)
    private NotetakerEndReason endReason;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "run_token_hash", length = 64)
    private String runTokenHash;

    @Column(name = "launched_at")
    private Instant launchedAt;

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    @Column(name = "joined_at")
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "remove_requested_by")
    private UUID removeRequestedBy;

    @Column(name = "remove_requested_at")
    private Instant removeRequestedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "media")
    private RecordedMedia media;

    public void markLaunched(String externalId, Instant now) {
        this.externalId = externalId;
        this.launchedAt = now;
    }

    public void reportWaiting(Instant now) {
        lastHeartbeatAt = now;
        if (status == NotetakerStatus.LAUNCHING) {
            status = NotetakerStatus.WAITING_ROOM;
        }
    }

    public void reportRecording(Instant now) {
        lastHeartbeatAt = now;
        if (status == NotetakerStatus.LAUNCHING || status == NotetakerStatus.WAITING_ROOM) {
            status = NotetakerStatus.RECORDING;
            joinedAt = now;
        }
    }

    public void heartbeat(Instant now) {
        lastHeartbeatAt = now;
    }

    /** Whether the bot is to leave: the answer every heartbeat carries. */
    public boolean isToLeave() {
        return status == NotetakerStatus.REMOVE_REQUESTED;
    }

    public void requestRemoval(UUID userId, Instant now) {
        status = NotetakerStatus.REMOVE_REQUESTED;
        removeRequestedBy = userId;
        removeRequestedAt = now;
    }

    /**
     * The bot has left. Asked to, what it captured is kept by nobody (the epic's default until decided otherwise);
     * otherwise its media goes to be transcribed.
     */
    public void complete(RecordedMedia captured, Instant now) {
        leftAt = now;
        runTokenHash = null;
        if (status == NotetakerStatus.REMOVE_REQUESTED) {
            status = NotetakerStatus.REMOVED;
            return;
        }
        media = captured;
        status = NotetakerStatus.PROCESSING;
    }

    /** A bot asked to leave that then fails has still left: the removal is what the call saw. */
    public void fail(NotetakerEndReason reason, Instant now) {
        if (joinedAt != null && leftAt == null) {
            leftAt = now;
        }
        runTokenHash = null;
        if (status == NotetakerStatus.REMOVE_REQUESTED) {
            status = NotetakerStatus.REMOVED;
            return;
        }
        status = NotetakerStatus.FAILED;
        endReason = reason;
    }
}
