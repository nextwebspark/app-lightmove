package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.notetaker.model.RecallBotNotice;
import app.lightmove.api.notetaker.model.RecallLiveUtterance;
import app.lightmove.api.notetaker.model.RecallBotRequest;
import app.lightmove.api.notetaker.model.RecallBotState;
import app.lightmove.api.notetaker.model.TranscriptLine;
import java.net.URI;
import java.util.List;

/** No Recall bot account: no Notetaker is offered, and every webhook delivery is refused. */
public class UnconfiguredRecallBotApi implements RecallBotApi {

    @Override
    public boolean isOffered() {
        return false;
    }

    @Override
    public String send(RecallBotRequest request) {
        throw ApiException.of(ErrorCode.NOTETAKER_NOT_OFFERED);
    }

    @Override
    public void updateLive(String botId, URI liveTranscriptUrl) {
        throw ApiException.of(ErrorCode.NOTETAKER_NOT_OFFERED);
    }

    @Override
    public List<RecallLiveUtterance> liveUtterances(byte[] body, String id, String timestamp, String signature) {
        throw ApiException.of(ErrorCode.NOTETAKER_WEBHOOK_REJECTED);
    }

    @Override
    public void remove(String botId) {
    }

    @Override
    public RecallBotState state(String botId) {
        throw ApiException.of(ErrorCode.NOTETAKER_NOT_OFFERED);
    }

    @Override
    public void deleteMedia(String botId) {
    }

    @Override
    public List<TranscriptLine> transcript(String transcriptId) {
        throw ApiException.of(ErrorCode.NOTETAKER_NOT_OFFERED);
    }

    @Override
    public List<RecallBotNotice> notices(byte[] body, String id, String timestamp, String signature) {
        throw ApiException.of(ErrorCode.NOTETAKER_WEBHOOK_REJECTED);
    }
}
