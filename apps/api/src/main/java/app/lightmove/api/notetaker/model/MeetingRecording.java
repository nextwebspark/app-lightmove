package app.lightmove.api.notetaker.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.notetaker.constant.NotetakerStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One Notetaker bot sent into a call with a person (V151), and what it brought back. The call is snapshotted rather
 * than linked, because a calendar's re-read replaces its meeting rows; a status only ever moves forward.
 */
@Entity
@Table(name = "app_lm_meeting_recording")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingRecording extends BaseEntity {

    private static final int LONGEST_REASON = 64;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "person_id", nullable = false, updatable = false)
    private UUID personId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "candidate_id", updatable = false)
    private UUID candidateId;

    @Column(name = "meeting_id", updatable = false)
    private UUID meetingId;

    @Column(name = "title", updatable = false)
    private String title;

    @Column(name = "starts_at", nullable = false, updatable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false, updatable = false)
    private Instant endsAt;

    @Column(name = "join_url", nullable = false, updatable = false)
    private String joinUrl;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Column(name = "recall_bot_id")
    private String recallBotId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private NotetakerStatus status;

    @Column(name = "recall_status")
    private String recallStatus;

    @Column(name = "recall_sub_code")
    private String recallSubCode;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "transcript_id")
    private String transcriptId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transcript", columnDefinition = "jsonb")
    private List<TranscriptLine> transcript;

    @Column(name = "note_id")
    private UUID noteId;

    @Column(name = "media_deleted", nullable = false)
    private boolean mediaDeleted;

    public static MeetingRecording claimed(UUID workspaceId, UUID personId, UUID projectId, UUID candidateId,
                                           UUID meetingId, String title, Instant startsAt, Instant endsAt,
                                           String joinUrl, UUID requestedBy) {
        MeetingRecording recording = new MeetingRecording();
        recording.workspaceId = workspaceId;
        recording.personId = personId;
        recording.projectId = projectId;
        recording.candidateId = candidateId;
        recording.meetingId = meetingId;
        recording.title = title;
        recording.startsAt = startsAt;
        recording.endsAt = endsAt;
        recording.joinUrl = joinUrl;
        recording.requestedBy = requestedBy;
        recording.status = NotetakerStatus.SCHEDULED;
        return recording;
    }

    public void sent(String botId) {
        this.recallBotId = botId;
    }

    /** Recall's own word for where the bot is; it moves our status forward only, since deliveries can cross. */
    public void heard(String code, String subCode, NotetakerStatus next) {
        this.recallStatus = clip(code, 64);
        this.recallSubCode = clip(subCode, 128);
        if (next != null && status.isLive() && next.ordinal() > status.ordinal()) {
            this.status = next;
        }
    }

    public void transcriptReady(String id) {
        this.transcriptId = id;
        if (status.isLive()) {
            this.status = NotetakerStatus.PROCESSING;
        }
    }

    public void transcribed(List<TranscriptLine> lines) {
        this.transcript = List.copyOf(lines);
    }

    public void filed(UUID noteId) {
        this.noteId = noteId;
        this.failureReason = null;
        this.status = NotetakerStatus.DONE;
    }

    public void mediaDeleted() {
        this.mediaDeleted = true;
    }

    public void failed(String reason) {
        this.failureReason = reason == null || reason.length() <= LONGEST_REASON ? reason
                : reason.substring(0, LONGEST_REASON);
        this.status = NotetakerStatus.FAILED;
    }

    public void cancelled() {
        this.status = NotetakerStatus.CANCELLED;
    }

    /** A failed recording is tried again only from its transcript: the call itself cannot be recorded twice. */
    public boolean isRetryable() {
        return status == NotetakerStatus.FAILED && recallBotId != null && noteId == null && !mediaDeleted
                && (transcriptId != null || transcript != null);
    }

    public void retrying() {
        this.failureReason = null;
        this.status = NotetakerStatus.PROCESSING;
    }

    private static String clip(String value, int longest) {
        return value == null || value.length() <= longest ? value : value.substring(0, longest);
    }
}
