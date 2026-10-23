package app.lightmove.api.notetaker.dto;

import app.lightmove.api.notetaker.constant.NotetakerStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * One Notetaker bot as the drawer's Meetings section draws it. {@code meetingId} and {@code startsAt} are the call it
 * was sent to, matched by either, since a calendar's re-read gives the same call a new row. {@code failureReason} is
 * Recall's sub-code or our own word, explained on screen.
 */
public record NotetakerRecordingResponse(UUID id, UUID meetingId, Instant startsAt, NotetakerStatus status,
                                         String failureReason, boolean hasTranscript, boolean retryable,
                                         UUID noteId) {}
