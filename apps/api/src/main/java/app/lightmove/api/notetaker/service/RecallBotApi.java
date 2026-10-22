package app.lightmove.api.notetaker.service;

import app.lightmove.api.notetaker.model.RecallBotNotice;
import app.lightmove.api.notetaker.model.RecallBotRequest;
import app.lightmove.api.notetaker.model.TranscriptLine;
import java.util.List;

/**
 * Recall.ai's bots, the backup behind our own (#955): one account for the platform, {@code lightmove.recall.*}. Without
 * a key nothing is offered, and a bot of ours that fails simply fails.
 */
public interface RecallBotApi {

    boolean isOffered();

    /** Sends a bot into the call now; answers Recall's id for it. Never retried. */
    String send(RecallBotRequest request);

    /** Asks a bot to leave its call. */
    void leave(String botId);

    /** Deletes what Recall kept of the call: once its transcript is ours, or once nobody wants it. */
    void deleteMedia(String botId);

    /** The call's transcript, a line per turn, each named by the participant Recall heard. */
    List<TranscriptLine> transcript(String transcriptId);

    /** What a webhook delivery says; refused with {@code NOTETAKER_WEBHOOK_REJECTED} when its signature fails. */
    List<RecallBotNotice> notices(byte[] body, String id, String timestamp, String signature);
}
