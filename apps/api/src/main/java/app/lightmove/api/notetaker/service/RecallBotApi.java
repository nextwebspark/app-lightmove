package app.lightmove.api.notetaker.service;

import app.lightmove.api.notetaker.model.RecallBotNotice;
import app.lightmove.api.notetaker.model.RecallBotRequest;
import app.lightmove.api.notetaker.model.RecallBotState;
import app.lightmove.api.notetaker.model.TranscriptLine;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Recall.ai's meeting bots, the Notetaker's recorder: one account for the platform, {@code lightmove.recall.*}.
 * Without a key and a bot webhook secret nothing is offered.
 */
public interface RecallBotApi {

    /**
     * How long Recall keeps a call's media after the call: sent with every bot, so Recall deletes the recording by
     * itself. Seven days is the span its storage is free for.
     */
    Duration MEDIA_RETENTION = Duration.ofDays(7);

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

    /** Deletes what Recall kept of the call: a bot the consultant discarded. */
    void deleteMedia(String botId);

    /**
     * A link to the call's mixed video while Recall still holds it; empty once it expired or was deleted. The link is
     * pre-signed and short-lived, so it is read afresh for each download and never handed to a browser.
     */
    Optional<URI> recordingVideo(String botId);

    /** Copies the file behind a link {@link #recordingVideo} gave; refused for any host but Recall's or AWS's. */
    void copyVideo(URI video, OutputStream out) throws IOException;

    /** The call's transcript, a line per turn, each named by the participant Recall heard. */
    List<TranscriptLine> transcript(String transcriptId);

    /** What a webhook delivery says; refused with {@code NOTETAKER_WEBHOOK_REJECTED} when its signature fails. */
    List<RecallBotNotice> notices(byte[] body, String id, String timestamp, String signature);
}
