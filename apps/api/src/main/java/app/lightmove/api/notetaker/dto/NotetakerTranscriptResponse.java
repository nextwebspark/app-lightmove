package app.lightmove.api.notetaker.dto;

import app.lightmove.api.notetaker.constant.NotetakerStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A recorded call's transcript. {@code complete} is false while the call goes or its note is being made, when the
 * lines are those heard so far; {@code recordedUntil} is set when the bot stopped before the call ended.
 */
public record NotetakerTranscriptResponse(UUID recordingId, NotetakerStatus status, boolean complete,
                                          Instant recordedUntil, List<TranscriptLineResponse> lines) {}
