package app.lightmove.api.notetaker.model;

import java.util.UUID;

/** New finished lines of a live call are on file; the coach decides, after the commit, whether a run is due. */
public record LiveLinesHeard(UUID recordingId) {}
