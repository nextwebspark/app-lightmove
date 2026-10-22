package app.lightmove.api.notetaker.dto;

import app.lightmove.api.notetaker.constant.NotetakerStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A recorded call's transcript, with the call as it was when the bot went in. {@code complete} is false while the call
 * goes or its note is being made, when the lines are those heard so far; {@code recordedUntil} is set when the bot
 * stopped before the call ended. Elapsed times count from {@code joinedAt}, as the note's evidence does.
 */
public record NotetakerTranscriptResponse(UUID recordingId, NotetakerStatus status, boolean complete,
                                          String title, Instant startsAt, Instant endsAt, Instant joinedAt,
                                          String consultantName, Instant recordedUntil,
                                          List<TranscriptLineResponse> lines) {}
