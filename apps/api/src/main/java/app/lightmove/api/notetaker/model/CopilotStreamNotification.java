package app.lightmove.api.notetaker.model;

import java.util.UUID;

/** A live call's copilot changed — a line, a reading, a status or who is watching; the window reads it back. */
public record CopilotStreamNotification(UUID recordingId) {}
