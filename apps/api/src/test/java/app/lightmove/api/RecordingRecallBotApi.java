package app.lightmove.api;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.notetaker.model.RecallBotNotice;
import app.lightmove.api.notetaker.model.RecallBotRequest;
import app.lightmove.api.notetaker.model.RecallBotState;
import app.lightmove.api.notetaker.model.TranscriptLine;
import app.lightmove.api.notetaker.service.RecallBotApi;
import app.lightmove.api.notetaker.service.RecallBotClient;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * A Recall that remembers every bot it was asked for, called back, took out of a call and wiped, serves the
 * transcripts and bot states a test hands it, and reads webhook bodies as the real client does, so a test posts
 * Recall's own JSON.
 */
public class RecordingRecallBotApi implements RecallBotApi {

    /** The one signature {@link #notices} accepts; anything else is refused as a forged delivery. */
    public static final String VALID_SIGNATURE = "v1,signed-by-recall-bots";

    private final List<RecallBotRequest> sent = new CopyOnWriteArrayList<>();
    private final List<String> removed = new CopyOnWriteArrayList<>();
    private final List<String> left = new CopyOnWriteArrayList<>();
    private volatile VendorFailureKind leaveRefusal;
    private final List<String> mediaDeleted = new CopyOnWriteArrayList<>();
    private final Map<String, URI> liveUpdates = new ConcurrentHashMap<>();
    private final Map<String, String> consentLines = new ConcurrentHashMap<>();
    private volatile boolean failUpdates;
    private final List<String> transcriptRequests = new CopyOnWriteArrayList<>();
    private final Map<String, String> transcriptStatuses = new ConcurrentHashMap<>();
    private volatile boolean failTranscriptRequests;
    private final Map<String, List<TranscriptLine>> transcripts = new ConcurrentHashMap<>();
    private final Map<String, RecallBotState> states = new ConcurrentHashMap<>();
    private final List<String> botIds = new CopyOnWriteArrayList<>();
    private volatile boolean offered = true;
    private volatile boolean failSends;
    private volatile boolean busySends;
    private volatile Runnable whileSending = () -> { };

    @Override
    public boolean isOffered() {
        return offered;
    }

    @Override
    public String send(RecallBotRequest request) {
        if (failSends) {
            throw new VendorException(VendorCall.of("recall", "bot-create"), VendorFailureKind.UNAVAILABLE, null);
        }
        if (busySends) {
            throw ApiException.of(ErrorCode.NOTETAKER_BUSY);
        }
        whileSending.run();
        sent.add(request);
        String botId = "bot-" + UUID.randomUUID();
        botIds.add(botId);
        return botId;
    }

    @Override
    public void updateLive(String botId, URI liveTranscriptUrl, String consentLine) {
        if (failUpdates) {
            throw new VendorException(VendorCall.of("recall", "bot-update"), VendorFailureKind.BAD_REQUEST, null);
        }
        liveUpdates.put(botId, liveTranscriptUrl == null ? URI.create("off:none") : liveTranscriptUrl);
        consentLines.put(botId, consentLine);
    }

    @Override
    public boolean verifiesLive(byte[] body, String id, String timestamp, String signature) {
        return VALID_SIGNATURE.equals(signature);
    }

    @Override
    public String requestTranscript(String recallRecordingId) {
        if (failTranscriptRequests) {
            throw new VendorException(VendorCall.of("recall", "transcript-create"), VendorFailureKind.BAD_REQUEST,
                    null);
        }
        String transcriptId = "final-" + recallRecordingId;
        transcriptRequests.add(recallRecordingId);
        return transcriptId;
    }

    @Override
    public String transcriptStatus(String transcriptId) {
        return transcriptStatuses.getOrDefault(transcriptId, "processing");
    }

    @Override
    public void remove(String botId) {
        removed.add(botId);
    }

    @Override
    public void leave(String botId) {
        if (leaveRefusal != null) {
            throw new VendorException(VendorCall.of("recall", "bot-leave"), leaveRefusal, null);
        }
        left.add(botId);
    }

    @Override
    public RecallBotState state(String botId) {
        RecallBotState state = states.get(botId);
        if (state == null) {
            throw new VendorException(VendorCall.of("recall", "bot-read"), VendorFailureKind.NOT_FOUND, null);
        }
        return state;
    }

    @Override
    public void deleteMedia(String botId) {
        mediaDeleted.add(botId);
    }

    @Override
    public List<TranscriptLine> transcript(String transcriptId) {
        List<TranscriptLine> lines = transcripts.get(transcriptId);
        if (lines == null) {
            throw new VendorException(VendorCall.of("recall", "transcript"), VendorFailureKind.NOT_FOUND, null);
        }
        return lines;
    }

    @Override
    public List<RecallBotNotice> notices(byte[] body, String id, String timestamp, String signature) {
        if (!VALID_SIGNATURE.equals(signature)) {
            throw ApiException.of(ErrorCode.NOTETAKER_WEBHOOK_REJECTED);
        }
        return RecallBotClient.noticesIn(body);
    }

    public void holdsTranscript(String transcriptId, List<TranscriptLine> lines) {
        transcripts.put(transcriptId, lines);
    }

    public void botIs(String botId, RecallBotState state) {
        states.put(botId, state);
    }

    public void offered(boolean isOffered) {
        this.offered = isOffered;
    }

    /** What each bot's live transcript was last changed to: its endpoint, or {@code off:none} for none. */
    public Map<String, URI> liveUpdates() {
        return Map.copyOf(liveUpdates);
    }

    /** What each bot was last told to say as it joins, by a switch of its copilot. */
    public Map<String, String> consentLines() {
        return Map.copyOf(consentLines);
    }

    /** Recall's recordings a final transcript was asked of, oldest first; each answers {@code final-<recording>}. */
    public List<String> transcriptRequests() {
        return List.copyOf(transcriptRequests);
    }

    public void transcriptIs(String transcriptId, String status) {
        transcriptStatuses.put(transcriptId, status);
    }

    public void failTranscriptRequests(boolean fail) {
        this.failTranscriptRequests = fail;
    }

    public void failUpdates(boolean fail) {
        this.failUpdates = fail;
    }

    public void failSends(boolean fail) {
        this.failSends = fail;
    }

    /** Recall's pool of bots that join at once is empty: every send is refused and no bot is made. */
    public void busySends(boolean busy) {
        this.busySends = busy;
    }

    /** How Recall refuses a leave from now on; null takes every one. */
    public void refuseLeaves(VendorFailureKind refusal) {
        this.leaveRefusal = refusal;
    }

    /** Bots asked to leave their call, oldest first. */
    public List<String> left() {
        return List.copyOf(left);
    }

    /** Runs while Recall is being asked, before it answers: where a Remove pressed mid-send lands. */
    public void whileSending(Runnable action) {
        this.whileSending = action;
    }

    public List<RecallBotRequest> sent() {
        return List.copyOf(sent);
    }

    /** Recall's id for each bot sent, oldest first; ids are unique across the suite, as the column demands. */
    public List<String> botIds() {
        return List.copyOf(botIds);
    }

    public String lastBotId() {
        return botIds.getLast();
    }

    public List<String> removed() {
        return List.copyOf(removed);
    }

    public List<String> mediaDeleted() {
        return List.copyOf(mediaDeleted);
    }

    public void clear() {
        sent.clear();
        botIds.clear();
        removed.clear();
        left.clear();
        leaveRefusal = null;
        mediaDeleted.clear();
        liveUpdates.clear();
        consentLines.clear();
        failUpdates = false;
        transcriptRequests.clear();
        transcriptStatuses.clear();
        failTranscriptRequests = false;
        transcripts.clear();
        states.clear();
        offered = true;
        failSends = false;
        busySends = false;
        whileSending = () -> { };
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        /** {@code @Primary} so it wins over the unconfigured API a test profile without a Recall key builds. */
        @Bean
        @Primary
        public RecordingRecallBotApi recordingRecallBotApi() {
            return new RecordingRecallBotApi();
        }
    }
}
