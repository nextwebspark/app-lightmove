package app.lightmove.api.notetaker.model;

import java.time.Instant;

/** What one verified Recall delivery says about a backup bot. */
public sealed interface RecallBotNotice {

    String botId();

    /** The bot moved: {@code code} is Recall's own (in_waiting_room, in_call_recording, done, fatal, …). */
    record StatusChanged(String botId, String code, String subCode, Instant at) implements RecallBotNotice {}

    /** The call's transcript is ready to read. */
    record TranscriptReady(String botId, String transcriptId) implements RecallBotNotice {}

    /** The call's transcript will never come. */
    record TranscriptFailed(String botId) implements RecallBotNotice {}
}
