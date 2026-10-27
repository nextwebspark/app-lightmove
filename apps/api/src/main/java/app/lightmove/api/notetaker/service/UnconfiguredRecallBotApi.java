package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.notetaker.model.RecallBotNotice;
import app.lightmove.api.notetaker.model.RecallBotRequest;
import app.lightmove.api.notetaker.model.RecallBotState;
import app.lightmove.api.notetaker.model.TranscriptLine;
import java.io.OutputStream;
import java.net.URI;
import java.util.List;
import java.util.Optional;

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
    public void updateLive(String botId, URI liveTranscriptUrl, String consentLine) {
        throw ApiException.of(ErrorCode.NOTETAKER_NOT_OFFERED);
    }

    @Override
    public boolean verifiesLive(byte[] body, String id, String timestamp, String signature) {
        return false;
    }

    @Override
    public String requestTranscript(String recallRecordingId) {
        throw ApiException.of(ErrorCode.NOTETAKER_NOT_OFFERED);
    }

    @Override
    public String transcriptStatus(String transcriptId) {
        throw ApiException.of(ErrorCode.NOTETAKER_NOT_OFFERED);
    }

    @Override
    public void remove(String botId) {
    }

    @Override
    public void leave(String botId) {
    }

    @Override
    public RecallBotState state(String botId) {
        throw ApiException.of(ErrorCode.NOTETAKER_NOT_OFFERED);
    }

    @Override
    public void deleteMedia(String botId) {
    }

    @Override
    public Optional<URI> recordingVideo(String botId) {
        throw ApiException.of(ErrorCode.NOTETAKER_NOT_OFFERED);
    }

    @Override
    public void copyVideo(URI video, OutputStream out) {
        throw ApiException.of(ErrorCode.NOTETAKER_NOT_OFFERED);
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
