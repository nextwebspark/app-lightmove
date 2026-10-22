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

/**
 * One Notetaker bot on one call (V150). Inserted by {@code MeetingRecordingClaims}, whose unique call key is the
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

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "note_id")
    private UUID noteId;

    @Column(name = "recorded_until")
    private Instant recordedUntil;

    @Column(name = "processing_attempts", nullable = false)
    private int processingAttempts;

    @Column(name = "processed_at")
    private Instant processedAt;

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
     * The bot has left. Asked to, what it captured is kept by nobody ({@code NotetakerMediaPurge} deletes it);
     * otherwise its pieces go to be transcribed.
     */
    public void complete(Instant now) {
        leftAt = now;
        runTokenHash = null;
        if (status == NotetakerStatus.REMOVE_REQUESTED) {
            status = NotetakerStatus.REMOVED;
            return;
        }
        status = NotetakerStatus.PROCESSING;
    }

    /**
     * The bot stopped mid-call — it crashed or fell silent — having captured something: what it got is still
     * turned into a note, marked as stopping at {@code recordedUntil}. Removal still wins, keeping nothing.
     */
    public void stopEarly(Instant recordedUntil, Instant now) {
        if (status == NotetakerStatus.REMOVE_REQUESTED) {
            fail(NotetakerEndReason.CRASHED, now);
            return;
        }
        this.recordedUntil = recordedUntil;
        if (leftAt == null) {
            leftAt = now;
        }
        runTokenHash = null;
        status = NotetakerStatus.PROCESSING;
    }

    public boolean isProcessing() {
        return status == NotetakerStatus.PROCESSING;
    }

    /** The note is filed; {@code noteId} is null for a call in which nobody was heard. */
    public void noted(UUID projectId, UUID noteId, Instant now) {
        this.projectId = projectId;
        this.noteId = noteId;
        processedAt = now;
        status = NotetakerStatus.DONE;
    }

    /** Answers whether the run has used its tries and now waits for a consultant to press Try again. */
    public boolean processingFailed(int allowedAttempts, Instant now) {
        processingAttempts++;
        if (processingAttempts < allowedAttempts) {
            return false;
        }
        status = NotetakerStatus.FAILED;
        endReason = NotetakerEndReason.PROCESSING_FAILED;
        processedAt = now;
        return true;
    }

    public boolean isRetryable() {
        return status == NotetakerStatus.FAILED && endReason == NotetakerEndReason.PROCESSING_FAILED;
    }

    public void retryProcessing() {
        status = NotetakerStatus.PROCESSING;
        endReason = null;
        processingAttempts = 0;
        processedAt = null;
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
