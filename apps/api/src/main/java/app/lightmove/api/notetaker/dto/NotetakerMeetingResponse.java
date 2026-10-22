package app.lightmove.api.notetaker.dto;

import app.lightmove.api.notetaker.constant.MeetingRecorderKind;
import app.lightmove.api.notetaker.constant.NotetakerEndReason;
import app.lightmove.api.notetaker.constant.NotetakerPlatform;
import app.lightmove.api.notetaker.constant.NotetakerStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * The Notetaker on one of an executive's meetings, keyed on the meeting id the drawer already holds.
 * {@code joinable}: a call our bot can join; {@code enabled}: nobody turned it off for this call;
 * {@code status} is null until a bot was sent (or the call skipped). {@code recordedUntil}: a partial note's end.
 * {@code transcriptHere}: its note is filed under this position (or about the person), whose drawer may read the
 * transcript, and {@code noteId} names that note;
 * {@code notesElsewhere}: filed under another position, which this one never names.
 */
public record NotetakerMeetingResponse(UUID meetingId, NotetakerPlatform platform, boolean joinable, boolean enabled,
                                       UUID recordingId, NotetakerStatus status, NotetakerEndReason endReason,
                                       MeetingRecorderKind recorder, Instant joinedAt, Instant recordedUntil,
                                       boolean transcriptHere, UUID noteId, boolean notesElsewhere) {}
