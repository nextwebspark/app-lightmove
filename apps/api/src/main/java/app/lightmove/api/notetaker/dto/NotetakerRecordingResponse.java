package app.lightmove.api.notetaker.dto;

import app.lightmove.api.notetaker.constant.LiveCopilotStatus;
import app.lightmove.api.notetaker.constant.NotetakerStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * One Notetaker bot as the Meetings section draws it; a calendar re-read gives the call a new row, so {@code startsAt}
 * and {@code title} match it too, and a bot whose call left every calendar is its own row. {@code joinAt} is null for a
 * bot sent at once; the {@code may*} flags hold only for the bot's sender.
 */
public record NotetakerRecordingResponse(UUID id, UUID meetingId, Instant startsAt, Instant endsAt, String title,
                                         NotetakerStatus status, String failureReason, boolean hasTranscript,
                                         boolean retryable, UUID noteId, boolean suggestionsAsked,
                                         int pendingSuggestions, int appliedSuggestions, int foundSuggestions,
                                         boolean suggesting, boolean liveCopilot,
                                         LiveCopilotStatus liveStatus, boolean mayChangeCopilot, Instant joinAt,
                                         Instant recordingStartedAt, boolean endRequested,
                                         Instant recordingAvailableUntil, boolean mayDownloadRecording) {}
