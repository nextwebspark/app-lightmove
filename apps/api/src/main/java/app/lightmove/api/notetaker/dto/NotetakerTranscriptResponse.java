package app.lightmove.api.notetaker.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A recorded call's transcript, staff-only like the note it became. */
public record NotetakerTranscriptResponse(UUID recordingId, String title, Instant startsAt,
                                          List<TranscriptLineResponse> lines) {}
