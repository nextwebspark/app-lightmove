package app.lightmove.api.notetaker.model;

import java.util.UUID;

/** A recording whose transcript Recall has finished, ready to be turned into its note once this commits. */
public record MeetingTranscriptReady(UUID recordingId) {}
