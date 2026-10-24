package app.lightmove.api.notetaker.dto;

import app.lightmove.api.notetaker.constant.NotetakerStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * One Notetaker bot as the drawer's Meetings section draws it. {@code meetingId} is the row it was sent for; a
 * calendar's re-read gives the same call a new row, so {@code startsAt} and {@code title} match it too.
 * {@code failureReason} is Recall's sub-code or our own word, explained on screen. {@code suggestionsAsked},
 * {@code pendingSuggestions} and {@code appliedSuggestions} word the row's Suggest changes link, or what the review
 * made of it.
 */
public record NotetakerRecordingResponse(UUID id, UUID meetingId, Instant startsAt, String title,
                                         NotetakerStatus status, String failureReason, boolean hasTranscript,
                                         boolean retryable, UUID noteId, boolean suggestionsAsked,
                                         int pendingSuggestions, int appliedSuggestions) {}
