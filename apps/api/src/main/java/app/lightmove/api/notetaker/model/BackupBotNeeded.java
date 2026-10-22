package app.lightmove.api.notetaker.model;

import java.util.UUID;

/** A run our bot gave up mid-way was handed to the backup recorder, whose bot is sent once that commits. */
public record BackupBotNeeded(UUID recordingId) {}
