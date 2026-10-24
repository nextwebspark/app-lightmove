package app.lightmove.api.notetaker.model;

import java.time.Instant;

/**
 * A bot as Recall answers a read of it: its latest status, and the transcript of its recording once one exists. Read
 * only when a webhook may have been lost.
 */
public record RecallBotState(String statusCode, String subCode, Instant statusAt, String transcriptId,
                             String transcriptStatus, String recordingId) {}
