package app.lightmove.api.notetaker.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.core.security.token.Tokens;
import app.lightmove.api.notetaker.constant.CallSuggestionStatus;
import app.lightmove.api.notetaker.constant.CoachState;
import app.lightmove.api.notetaker.constant.LiveCopilotStatus;
import app.lightmove.api.notetaker.constant.NotetakerStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
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
    public static final String NEVER_RECORDED = "NEVER_RECORDED";

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

    /** Null for a bot sent at once, and while Join now is replacing a scheduled bot with one. */
    @Column(name = "join_at")
    private Instant joinAt;

    @Column(name = "recording_started_at")
    private Instant recordingStartedAt;

    @Column(name = "end_requested_at")
    private Instant endRequestedAt;

    @Column(name = "end_requested_by")
    private UUID endRequestedBy;

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

    @Column(name = "media_expires_at")
    private Instant mediaExpiresAt;

    /** Null until someone presses Suggest changes; empty when the call stated nothing new for the record. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "suggestions", columnDefinition = "jsonb")
    private List<CallSuggestion> suggestions;

    @Column(name = "suggested_at")
    private Instant suggestedAt;

    /** Suggest changes' claim: written only by the repository's claim and release, read to say the model is at it. */
    @Column(name = "suggesting_since", insertable = false, updatable = false)
    private Instant suggestingSince;

    /** Chosen before the bot joins: Recall streams a transcript only to a bot made for it. */
    @Column(name = "live_copilot", nullable = false)
    private boolean liveCopilot;

    @Column(name = "live_enabled_by")
    private UUID liveEnabledBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "live_status")
    private LiveCopilotStatus liveStatus;

    @Column(name = "live_stopped_by")
    private UUID liveStoppedBy;

    /** SHA-256 of the token in this bot's live transcript URL; only a delivery carrying it feeds this recording. */
    @Column(name = "live_token_hash")
    private String liveTokenHash;

    /** A live copilot bot's after-call transcript in Recall's accurate mode: the one its note is filed from. */
    @Column(name = "final_transcript_id")
    private String finalTranscriptId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "live", columnDefinition = "jsonb")
    private LiveCoaching live;

    @Column(name = "coached_at")
    private Instant coachedAt;

    /** The last live line the coach has read: a run reads the call through here, the next one past it. */
    @Column(name = "coached_through", nullable = false)
    private long coachedThrough;

    @Column(name = "coach_runs", nullable = false)
    private int coachRuns;

    @Column(name = "coach_failures", nullable = false)
    private int coachFailures;

    /** A live call is one AI use, counted at its first coaching rather than per run. */
    @Column(name = "live_charged", nullable = false)
    private boolean liveCharged;

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

    public void liveCopilot(boolean enabled, UUID by, String liveTokenHash) {
        this.liveCopilot = enabled;
        this.liveEnabledBy = enabled ? by : null;
        this.liveTokenHash = enabled ? liveTokenHash : null;
        this.liveStatus = enabled ? LiveCopilotStatus.LISTENING : null;
        this.live = enabled ? LiveCoaching.empty() : null;
    }

    /** Lines are taken and coached only while the copilot listens on a bot still in the call. */
    public boolean isListening() {
        return liveCopilot && liveStatus == LiveCopilotStatus.LISTENING && status.isLive()
                && status != NotetakerStatus.PROCESSING;
    }

    public boolean holdsLiveToken(String token) {
        return liveTokenHash != null && token != null && !token.isBlank() && MessageDigest.isEqual(
                liveTokenHash.getBytes(StandardCharsets.US_ASCII),
                Tokens.hash(token).getBytes(StandardCharsets.US_ASCII));
    }

    /** A live copilot bot's own transcript is the low-latency stream's; its note waits for {@link #finalTranscriptId}. */
    public boolean isFinalTranscript(String id) {
        return !liveCopilot || (finalTranscriptId != null && finalTranscriptId.equals(id));
    }

    public void finalTranscriptAsked(String id) {
        this.finalTranscriptId = id;
    }

    public void liveFeedStopped() {
        if (liveStatus == LiveCopilotStatus.LISTENING) {
            this.liveStatus = LiveCopilotStatus.FEED_STOPPED;
        }
    }

    public void liveStopped(UUID by) {
        this.liveStatus = LiveCopilotStatus.STOPPED;
        this.liveStoppedBy = by;
    }

    public void coached(LiveCoaching reading, long through, Instant at) {
        this.live = reading;
        this.coachedThrough = Math.max(coachedThrough, through);
        this.coachedAt = at;
        this.coachRuns += 1;
        this.coachFailures = 0;
    }

    /** A run the model failed: the same lines are read again by the next one, the failures counted to a pause. */
    public void coachFailed(CoachState next, Instant at) {
        this.live = (live == null ? LiveCoaching.empty() : live).withCoach(next);
        this.coachedAt = at;
        this.coachRuns += 1;
        this.coachFailures += 1;
    }

    /** A stretch the prompt guard stopped is skipped, never read again, and coaching goes on after it. */
    public void coachSkipped(long through, Instant at) {
        this.live = (live == null ? LiveCoaching.empty() : live).withCoach(CoachState.GUARDED);
        this.coachedThrough = Math.max(coachedThrough, through);
        this.coachedAt = at;
        this.coachRuns += 1;
    }

    public void liveCoaching(LiveCoaching reading) {
        this.live = reading;
    }

    public void liveCharged() {
        this.liveCharged = true;
    }

    public void sent(String botId, Instant joinAt) {
        this.recallBotId = botId;
        this.joinAt = joinAt;
    }

    /** Join now's claim: the scheduled bot stays on the row until the one replacing it is Recall's. */
    public void joiningNow() {
        this.joinAt = null;
    }

    public void stayScheduledFor(Instant joinAt) {
        this.joinAt = joinAt;
    }

    /** Recall patches a bot only while it waits to join, which a bot sent at once never does. */
    public boolean isScheduledAhead() {
        return status == NotetakerStatus.SCHEDULED && recallBotId != null && joinAt != null;
    }

    /** Recall's own word for where the bot is; it moves our status forward only, since deliveries can cross. */
    public void heard(String code, String subCode, NotetakerStatus next) {
        this.recallStatus = clip(code, 64);
        this.recallSubCode = clip(subCode, 128);
        if (next != null && status.isLive() && next.ordinal() > status.ordinal()) {
            this.status = next;
        }
    }

    /** The call clock starts with the first recording Recall reports, however early or late that was. */
    public void recordingSince(Instant at) {
        if (recordingStartedAt == null && at != null && status == NotetakerStatus.RECORDING) {
            this.recordingStartedAt = at;
        }
    }

    public void endRequested(UUID by, Instant at) {
        this.endRequestedBy = by;
        this.endRequestedAt = at;
    }

    public void endRequestCleared() {
        this.endRequestedBy = null;
        this.endRequestedAt = null;
    }

    public boolean isEnding() {
        return endRequestedAt != null;
    }

    /** Where the call's clock starts: the recording's own start, or the booked one until Recall reports it. */
    public Instant callClockStart() {
        return recordingStartedAt != null ? recordingStartedAt : startsAt;
    }

    /** A replacement bot streams to a URL of its own, so the old bot's token feeds nothing any more. */
    public void rekeyLive(String liveTokenHash) {
        this.liveTokenHash = liveTokenHash;
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

    public void mediaHeldUntil(Instant expiresAt) {
        if (mediaExpiresAt == null) {
            this.mediaExpiresAt = expiresAt;
        }
    }

    /** Whether Recall still holds a finished call's recording, by the retention it was sent with. */
    public boolean isRecordingKept(Instant now) {
        return (status == NotetakerStatus.DONE || status == NotetakerStatus.FAILED) && !mediaDeleted
                && recallBotId != null && mediaExpiresAt != null && now.isBefore(mediaExpiresAt);
    }

    public void failed(String reason) {
        this.failureReason = reason == null || reason.length() <= LONGEST_REASON ? reason
                : reason.substring(0, LONGEST_REASON);
        this.status = NotetakerStatus.FAILED;
    }

    /**
     * Recall sends a bot's {@code call_ended}, which carries why it left, and its {@code done} in the same instant, and
     * either may arrive first: a {@code done} that failed the recording without a reason still takes the reason after.
     */
    public boolean awaitsItsReason() {
        return status == NotetakerStatus.FAILED && NEVER_RECORDED.equals(failureReason);
    }

    /** Only a live recording is called back: one a webhook already finished stays as it ended. */
    public void cancelled() {
        if (status.isLive()) {
            this.status = NotetakerStatus.CANCELLED;
        }
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

    /** Suggest changes is offered on a call whose notes are filed and whose transcript is still kept. */
    public boolean isSuggestible() {
        return status == NotetakerStatus.DONE && transcript != null;
    }

    /** The model is reading the transcript for changes: a claim taken after {@code staleBefore} is still alive. */
    public boolean isSuggesting(Instant staleBefore) {
        return suggestingSince != null && suggestingSince.isAfter(staleBefore);
    }

    public boolean hasPendingSuggestions() {
        return suggestions != null && suggestions.stream().anyMatch(CallSuggestion::isPending);
    }

    public int suggestionsWith(CallSuggestionStatus status) {
        return suggestions == null ? 0 : (int) suggestions.stream().filter(held -> held.status() == status).count();
    }

    /** Asking again keeps every decided suggestion beside the new ones, so what was applied stays on record. */
    public void suggested(List<CallSuggestion> found, Instant at) {
        List<CallSuggestion> next = new ArrayList<>(suggestions == null ? List.of()
                : suggestions.stream().filter(held -> !held.isPending()).toList());
        next.addAll(found);
        this.suggestions = List.copyOf(next);
        this.suggestedAt = at;
    }

    public void replaceSuggestion(CallSuggestion changed) {
        this.suggestions = suggestions.stream()
                .map(held -> held.id().equals(changed.id()) ? changed : held)
                .toList();
    }

    private static String clip(String value, int longest) {
        return value == null || value.length() <= longest ? value : value.substring(0, longest);
    }
}
