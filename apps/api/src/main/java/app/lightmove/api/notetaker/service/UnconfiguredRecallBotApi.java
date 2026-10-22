package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.notetaker.model.RecallBotNotice;
import app.lightmove.api.notetaker.model.RecallBotRequest;
import app.lightmove.api.notetaker.model.TranscriptLine;
import java.util.List;

/** No Recall key: no backup bot is ever sent, and every webhook delivery is refused. */
public class UnconfiguredRecallBotApi implements RecallBotApi {

    @Override
    public boolean isOffered() {
        return false;
    }

    @Override
    public String send(RecallBotRequest request) {
        throw new IllegalStateException("Recall is not configured");
    }

    @Override
    public void leave(String botId) {
    }

    @Override
    public void deleteMedia(String botId) {
    }

    @Override
    public List<TranscriptLine> transcript(String transcriptId) {
        return List.of();
    }

    @Override
    public List<RecallBotNotice> notices(byte[] body, String id, String timestamp, String signature) {
        throw ApiException.of(ErrorCode.NOTETAKER_WEBHOOK_REJECTED);
    }
}
