package app.lightmove.api.notetaker.service;

import app.lightmove.api.notetaker.constant.NotetakerStatus;
import app.lightmove.api.notetaker.model.MeetingRecording;
import app.lightmove.api.notetaker.model.MeetingTranscriptReady;
import app.lightmove.api.notetaker.model.RecallBotNotice;
import app.lightmove.api.notetaker.repository.MeetingRecordingRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * What Recall tells us about a Notetaker bot, folded onto its recording. A bot we hold no row for (one sent from
 * Recall's dashboard, or another deployment's on a shared account) is ignored; an ended recording takes no more news.
 * The safety net for a delivery that never comes is {@link NotetakerReconcile}, which reads the bot and lands here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotetakerWebhook {

    private final RecallBotApi recall;
    private final MeetingRecordingRepository recordings;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher events;

    public void receive(byte[] body, String id, String timestamp, String signature) {
        for (RecallBotNotice notice : recall.notices(body, id, timestamp, signature)) {
            transactions.executeWithoutResult(status -> recordings.findByRecallBotId(notice.botId())
                    .filter(recording -> recording.getStatus().isLive())
                    .ifPresent(recording -> apply(recording, notice)));
        }
    }

    /** Must run inside a transaction: a transcript that is ready is handed to the worker only once this commits. */
    void apply(MeetingRecording recording, RecallBotNotice notice) {
        switch (notice) {
            case RecallBotNotice.StatusChanged changed -> applyStatus(recording, changed.code(), changed.subCode());
            case RecallBotNotice.TranscriptReady ready -> {
                // Svix redelivers on a timeout: a transcript already handed over is not handed over again.
                if (!ready.transcriptId().equals(recording.getTranscriptId())) {
                    recording.transcriptReady(ready.transcriptId());
                    events.publishEvent(new MeetingTranscriptReady(recording.getId()));
                }
            }
            case RecallBotNotice.TranscriptFailed failed -> recording.failed("TRANSCRIPT_FAILED");
        }
    }

    void applyStatus(MeetingRecording recording, String code, String subCode) {
        switch (code) {
            case "joining_call", "in_waiting_room", "in_call_not_recording" ->
                    recording.heard(code, subCode, NotetakerStatus.JOINING);
            case "in_call_recording", "recording_permission_allowed" ->
                    recording.heard(code, subCode, NotetakerStatus.RECORDING);
            case "call_ended", "done" -> {
                if (recording.getStatus() == NotetakerStatus.RECORDING
                        || recording.getStatus() == NotetakerStatus.PROCESSING) {
                    recording.heard(code, subCode, NotetakerStatus.PROCESSING);
                } else {
                    // It left without ever recording: nobody let it in, the call never started, or the host refused.
                    recording.heard(code, subCode, null);
                    recording.failed(reasonOf(subCode, "NEVER_RECORDED"));
                }
            }
            case "fatal", "recording_permission_denied" -> {
                recording.heard(code, subCode, null);
                recording.failed(reasonOf(subCode, code.toUpperCase(Locale.ROOT)));
            }
            default -> recording.heard(code, subCode, null);
        }
        log.info("Notetaker recording {} heard {} ({}), now {}", recording.getId(), code, subCode,
                recording.getStatus());
    }

    private static String reasonOf(String subCode, String fallback) {
        return subCode == null || subCode.isBlank() ? fallback : subCode.strip().toLowerCase(Locale.ROOT);
    }
}
