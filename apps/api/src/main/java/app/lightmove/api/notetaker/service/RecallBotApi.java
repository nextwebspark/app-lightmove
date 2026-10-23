package app.lightmove.api.notetaker.service;

import app.lightmove.api.notetaker.model.RecallBotNotice;
import app.lightmove.api.notetaker.model.RecallLiveUtterance;
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

    /** Sends or schedules a bot; answers Recall's id for it. Never retried: a second bot in a call is worse. */
    String send(RecallBotRequest request);

    /** Turns a scheduled bot's live transcript on ({@code liveTranscriptUrl}) or off (null) before it joins. */
    void updateLive(String botId, URI liveTranscriptUrl);

    /** What a real-time delivery says; refused with {@code NOTETAKER_WEBHOOK_REJECTED} when its signature fails. */
    List<RecallLiveUtterance> liveUtterances(byte[] body, String id, String timestamp, String signature);

    /** Calls a bot back: a scheduled one is deleted, one already in the call is asked to leave. */
    void remove(String botId);

    RecallBotState state(String botId);

    /** Deletes what Recall kept of the call, once its transcript is ours. */
    void deleteMedia(String botId);

    /** The call's transcript, a line per turn, each named by the participant Recall heard. */
    List<TranscriptLine> transcript(String transcriptId);

    /** What a webhook delivery says; refused with {@code NOTETAKER_WEBHOOK_REJECTED} when its signature fails. */
    List<RecallBotNotice> notices(byte[] body, String id, String timestamp, String signature);
}
