package app.lightmove.api.notetaker.model;

import java.time.Instant;

/** What one verified Recall delivery says about a Notetaker bot. */
public sealed interface RecallBotNotice {

    String botId();

    /** The bot moved: {@code code} is Recall's own (in_waiting_room, in_call_recording, done, fatal, …). */
    record StatusChanged(String botId, String code, String subCode, Instant at) implements RecallBotNotice {}

    /**
     * A transcript of the call is ready to read. {@code botId} may be null for one asked of a recording after the call;
     * {@code recordingId} is Recall's recording, where the delivery names it.
     */
    record TranscriptReady(String botId, String transcriptId, String recordingId) implements RecallBotNotice {}

    /** A transcript of the call will never come. */
    record TranscriptFailed(String botId, String transcriptId, String recordingId) implements RecallBotNotice {}
}
