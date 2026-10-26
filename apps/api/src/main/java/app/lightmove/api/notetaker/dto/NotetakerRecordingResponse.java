package app.lightmove.api.notetaker.dto;

import app.lightmove.api.notetaker.constant.LiveCopilotStatus;
import app.lightmove.api.notetaker.constant.NotetakerStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * One Notetaker bot as the drawer's Meetings section draws it. {@code meetingId} is the row it was sent for; a
 * calendar's re-read gives the same call a new row, so {@code startsAt} and {@code title} match it too.
 * {@code failureReason} is Recall's sub-code or our own word, explained on screen. {@code suggestionsAsked},
 * {@code pendingSuggestions}, {@code appliedSuggestions} and {@code foundSuggestions} word the row's changes, or what
 * the review made of them, and {@code suggesting} that the model is reading the transcript for them now;
 * {@code liveCopilot} and {@code liveStatus} its live copilot's toggle and Open copilot link, and
 * {@code mayChangeCopilot} whether the caller, being the bot's sender, may switch it. {@code joinAt} is null for a bot
 * sent at once, {@code recordingStartedAt} where the call clock starts, and {@code endRequested} that End & file notes
 * was pressed and the bot is leaving.
 */
public record NotetakerRecordingResponse(UUID id, UUID meetingId, Instant startsAt, String title,
                                         NotetakerStatus status, String failureReason, boolean hasTranscript,
                                         boolean retryable, UUID noteId, boolean suggestionsAsked,
                                         int pendingSuggestions, int appliedSuggestions, int foundSuggestions,
                                         boolean suggesting, boolean liveCopilot,
                                         LiveCopilotStatus liveStatus, boolean mayChangeCopilot, Instant joinAt,
                                         Instant recordingStartedAt, boolean endRequested) {}
