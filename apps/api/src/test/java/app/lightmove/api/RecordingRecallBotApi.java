package app.lightmove.api;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.webhook.service.SvixSignatures;
import app.lightmove.api.notetaker.model.RecallBotNotice;
import app.lightmove.api.notetaker.model.RecallBotRequest;
import app.lightmove.api.notetaker.model.TranscriptLine;
import app.lightmove.api.notetaker.service.RecallBotApi;
import app.lightmove.api.notetaker.service.RecallBotClient;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Recall's bots without Recall: off until a test offers them, it remembers every bot sent, asked to leave and whose
 * media was deleted, answers the transcripts a test gives it, and checks webhook signatures under {@link #SECRET} as
 * the real client checks them under its own.
 */
public class RecordingRecallBotApi implements RecallBotApi {

    public static final String SECRET = "whsec_" + Base64.getEncoder()
            .encodeToString("notetaker-backup-test-secret".getBytes(StandardCharsets.UTF_8));

    private final List<RecallBotRequest> sent = new CopyOnWriteArrayList<>();
    private final List<String> askedToLeave = new CopyOnWriteArrayList<>();
    private final List<String> mediaDeleted = new CopyOnWriteArrayList<>();
    private final Map<String, List<TranscriptLine>> transcripts = new ConcurrentHashMap<>();
    private final List<String> botIds = new CopyOnWriteArrayList<>();
    private volatile boolean offered;
    private volatile boolean failSends;

    @Override
    public boolean isOffered() {
        return offered;
    }

    @Override
    public String send(RecallBotRequest request) {
        if (failSends) {
            throw new IllegalStateException("Recall refused the bot");
        }
        sent.add(request);
        String botId = "recall-bot-" + UUID.randomUUID();
        botIds.add(botId);
        return botId;
    }

    @Override
    public void leave(String botId) {
        askedToLeave.add(botId);
    }

    @Override
    public void deleteMedia(String botId) {
        mediaDeleted.add(botId);
    }

    @Override
    public List<TranscriptLine> transcript(String transcriptId) {
        return transcripts.getOrDefault(transcriptId, List.of());
    }

    @Override
    public List<RecallBotNotice> notices(byte[] body, String id, String timestamp, String signature) {
        if (!SvixSignatures.verifies(SECRET, id, timestamp, signature, body, Instant.now())) {
            throw ApiException.of(ErrorCode.NOTETAKER_WEBHOOK_REJECTED);
        }
        return RecallBotClient.noticesIn(body);
    }

    /** The {@code webhook-signature} a delivery of {@code body} carries. */
    public static String sign(String id, String timestamp, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(SECRET.substring("whsec_".length())),
                    "HmacSHA256"));
            return "v1," + Base64.getEncoder().encodeToString(
                    mac.doFinal((id + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public void offer(boolean offer) {
        offered = offer;
    }

    public void failSends(boolean fail) {
        failSends = fail;
    }

    public void transcriptIs(String transcriptId, List<TranscriptLine> lines) {
        transcripts.put(transcriptId, List.copyOf(lines));
    }

    /** The id Recall gave the bot sent last. */
    public String lastBotId() {
        return botIds.getLast();
    }

    public List<RecallBotRequest> sent() {
        return List.copyOf(sent);
    }

    public List<String> askedToLeave() {
        return List.copyOf(askedToLeave);
    }

    public List<String> mediaDeleted() {
        return List.copyOf(mediaDeleted);
    }

    public void clear() {
        sent.clear();
        askedToLeave.clear();
        mediaDeleted.clear();
        transcripts.clear();
        botIds.clear();
        offered = false;
        failSends = false;
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        @Primary
        public RecordingRecallBotApi recordingRecallBotApi() {
            return new RecordingRecallBotApi();
        }
    }
}
