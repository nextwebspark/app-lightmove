package app.lightmove.api.notetaker.service;

import app.lightmove.api.notetaker.model.RecallBotNotice;
import app.lightmove.api.notetaker.model.RecallBotRequest;
import app.lightmove.api.notetaker.model.RecallBotState;
import app.lightmove.api.notetaker.model.TranscriptLine;
import java.net.URI;
import java.util.List;

/**
 * Recall.ai's meeting bots, the Notetaker's recorder: one account for the platform, {@code lightmove.recall.*}.
 * Without a key and a bot webhook secret nothing is offered.
 */
public interface RecallBotApi {

    boolean isOffered();

    /**
     * Sends or schedules a bot; answers Recall's id for it. Never retried: a second bot in a call is worse. A bot sent
     * at once comes from Recall's warm pool, and an empty pool is {@code NOTETAKER_BUSY} with no bot made.
     */
    String send(RecallBotRequest request);

    /**
     * Turns a scheduled bot's live transcript on ({@code liveTranscriptUrl}) or off (null) before it joins, with what it
     * says in the chat as it does.
     */
    void updateLive(String botId, URI liveTranscriptUrl, String consentLine);

    /** Whether a real-time delivery carries a valid Svix signature under the bot webhook secret. */
    boolean verifiesLive(byte[] body, String id, String timestamp, String signature);

    /** Asks for an accuracy-mode transcript of a finished recording; answers the new transcript's id. */
    String requestTranscript(String recallRecordingId);

    /** A transcript's state as Recall answers it: {@code processing}, {@code done} or {@code failed}. */
    String transcriptStatus(String transcriptId);

    /** Calls a bot back: a scheduled one is deleted, one already in the call is asked to leave. */
    void remove(String botId);

    /** Takes a bot out of its call for good; what it recorded is transcribed as after any call. */
    void leave(String botId);

    RecallBotState state(String botId);

    /** Deletes what Recall kept of the call, once its transcript is ours. */
    void deleteMedia(String botId);

    /** The call's transcript, a line per turn, each named by the participant Recall heard. */
    List<TranscriptLine> transcript(String transcriptId);

    /** What a webhook delivery says; refused with {@code NOTETAKER_WEBHOOK_REJECTED} when its signature fails. */
    List<RecallBotNotice> notices(byte[] body, String id, String timestamp, String signature);
}
