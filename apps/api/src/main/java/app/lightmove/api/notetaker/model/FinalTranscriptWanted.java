package app.lightmove.api.notetaker.model;

import java.util.UUID;

/**
 * A live copilot call is over: its note waits for a transcript in Recall's accurate mode, asked after the commit.
 * {@code recallRecordingId} is Recall's recording where the delivery named it, null to read it off the bot.
 */
public record FinalTranscriptWanted(UUID recordingId, String recallRecordingId) {}
