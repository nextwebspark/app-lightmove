package app.lightmove.api.notetaker.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.notetaker.constant.MeetingRecorderKind;
import app.lightmove.api.notetaker.constant.NotetakerEndReason;
import app.lightmove.api.notetaker.constant.NotetakerPlatform;
import app.lightmove.api.notetaker.constant.NotetakerStatus;
import app.lightmove.api.notetaker.service.NotetakerStreamAnnouncer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One Notetaker bot on one call (V151). Inserted by {@code MeetingRecordingClaims}, whose unique call key is the
 * claim; every later step is a method here. The run token's hash is cleared once the bot can change nothing more.
 */
@Entity
@EntityListeners(NotetakerStreamAnnouncer.class)
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

    /** Why our bot was followed by the backup recorder; null while ours answers, and for a Zoom call it never joins. */
    @Enumerated(EnumType.STRING)
    @Column(name = "fallback_reason", length = 32)
    private NotetakerEndReason fallbackReason;

    /** When the backup recorder's bot got into the call. */
    @Column(name = "backup_joined_at")
    private Instant backupJoinedAt;

    /** When the backup's transcript was stored, or given up on: until then a backup run's note waits. */
    @Column(name = "backup_transcript_at")
    private Instant backupTranscriptAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private NotetakerStatus statusAnnounced;

    @PostLoad
    void rememberStatus() {
        statusAnnounced = status;
    }

    /** Whether the status moved since it was loaded or last announced, marking it announced. */
    public boolean takeStatusMove() {
        if (status == statusAnnounced) {
            return false;
        }
        statusAnnounced = status;
        return true;
    }

    public void markLaunched(String externalId, Instant now) {
        this.externalId = externalId;
        this.launchedAt = now;
    }

    /**
     * Sent to the backup recorder from the start: our bot could not be started ({@code reason}), or the call is one only
     * the backup joins (no reason). It reports by webhook, so no run token is kept.
     */
    public void launchedAsBackup(String externalId, NotetakerEndReason reason, Instant now) {
        recorder = MeetingRecorderKind.RECALL;
        fallbackReason = reason;
        runTokenHash = null;
        markLaunched(externalId, now);
    }

    /**
     * Our bot failed mid-run; the backup recorder takes the same run over. What ours captured stays, to be stitched
     * before the backup's part, and {@code ownStoppedAt} — where it stopped, null when it had heard nothing — marks the
     * start of the gap. Our bot's token goes, so a bot of ours still alive can change nothing more.
     */
    public void handToBackup(NotetakerEndReason reason, Instant ownStoppedAt, Instant now) {
        recorder = MeetingRecorderKind.RECALL;
        fallbackReason = reason;
        status = NotetakerStatus.LAUNCHING;
        externalId = null;
        runTokenHash = null;
        launchedAt = null;
        lastHeartbeatAt = null;
        recordedUntil = ownStoppedAt;
    }

    /** The backup's bot is in the call and recording. */
    public void backupJoined(Instant at) {
        lastHeartbeatAt = at;
        if (status == NotetakerStatus.LAUNCHING || status == NotetakerStatus.WAITING_ROOM) {
            status = NotetakerStatus.RECORDING;
        }
        if (joinedAt == null) {
            joinedAt = at;
        }
        if (backupJoinedAt == null) {
            backupJoinedAt = at;
        }
    }

    public void backupTranscribed(Instant now) {
        if (backupTranscriptAt == null) {
            backupTranscriptAt = now;
        }
    }

    /** A backup that got in has a transcript of its own to come, which the note waits for. */
    public boolean awaitsBackupTranscript() {
        return recorder == MeetingRecorderKind.RECALL && backupJoinedAt != null && backupTranscriptAt == null;
    }

    /** Our bot stopped and the backup's took over: the note marks the gap between the two. */
    public boolean resumedByBackup() {
        return recorder == MeetingRecorderKind.RECALL && backupJoinedAt != null && recordedUntil != null
                && !recordedUntil.isAfter(backupJoinedAt);
    }

    /** Where the recording stopped short, as the drawer says it; a call the backup picked back up did not. */
    public Instant stoppedShortAt() {
        return resumedByBackup() ? null : recordedUntil;
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
