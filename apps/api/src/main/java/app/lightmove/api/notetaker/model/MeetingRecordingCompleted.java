package app.lightmove.api.notetaker.model;

import java.util.UUID;

/** A bot left a call with something to transcribe. Published inside the transaction that recorded it. */
public record MeetingRecordingCompleted(UUID workspaceId, UUID recordingId) {}
