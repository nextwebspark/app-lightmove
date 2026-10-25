package app.lightmove.api.notetaker.service;

import app.lightmove.api.notetaker.constant.NotetakerStatus;
import app.lightmove.api.notetaker.model.FinalTranscriptWanted;
import app.lightmove.api.notetaker.model.MeetingRecording;
import app.lightmove.api.notetaker.model.MeetingTranscriptReady;
import app.lightmove.api.notetaker.model.RecallBotNotice;
import app.lightmove.api.notetaker.repository.MeetingLiveLineRepository;
import app.lightmove.api.notetaker.repository.MeetingRecordingRepository;
import java.util.Locale;
import java.util.Optional;
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
    private final MeetingLiveLineRepository liveLines;
    private final CopilotStreamPublisher copilotStream;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher events;

    public void receive(byte[] body, String id, String timestamp, String signature) {
        for (RecallBotNotice notice : recall.notices(body, id, timestamp, signature)) {
            transactions.executeWithoutResult(status -> recordingOf(notice)
                    .filter(recording -> recording.getStatus().isLive())
                    .ifPresent(recording -> apply(recording, notice)));
        }
    }

    private Optional<MeetingRecording> recordingOf(RecallBotNotice notice) {
        Optional<MeetingRecording> byBot = notice.botId() == null ? Optional.empty()
                : recordings.findByRecallBotId(notice.botId());
        if (byBot.isPresent()) {
            return byBot;
        }
        String transcriptId = switch (notice) {
            case RecallBotNotice.TranscriptReady ready -> ready.transcriptId();
            case RecallBotNotice.TranscriptFailed failed -> failed.transcriptId();
            case RecallBotNotice.StatusChanged changed -> null;
        };
        return transcriptId == null ? Optional.empty() : recordings.findByFinalTranscriptId(transcriptId);
    }

    /** Must run inside a transaction: a transcript that is ready is handed to the worker only once this commits. */
    void apply(MeetingRecording recording, RecallBotNotice notice) {
        switch (notice) {
            case RecallBotNotice.StatusChanged changed -> {
                applyStatus(recording, changed.code(), changed.subCode());
                if (recording.isLiveCopilot() && changed.code().equals("done")) {
                    wantFinalTranscript(recording, null);
                }
            }
            case RecallBotNotice.TranscriptReady ready when !recording.isFinalTranscript(ready.transcriptId()) -> {
                // A live copilot bot's own transcript is the low-latency stream: the note waits for the accurate one.
                recording.liveFeedStopped();
                wantFinalTranscript(recording, ready.recordingId());
            }
            case RecallBotNotice.TranscriptReady ready -> {
                // Svix redelivers on a timeout: a transcript already handed over is not handed over again.
                if (!ready.transcriptId().equals(recording.getTranscriptId())) {
                    recording.transcriptReady(ready.transcriptId());
                    events.publishEvent(new MeetingTranscriptReady(recording.getId()));
                }
            }
            case RecallBotNotice.TranscriptFailed failed when !recording.isFinalTranscript(failed.transcriptId()) -> {
                recording.liveFeedStopped();
                wantFinalTranscript(recording, failed.recordingId());
            }
            case RecallBotNotice.TranscriptFailed failed -> {
                recording.liveFeedStopped();
                recording.failed("TRANSCRIPT_FAILED");
            }
        }
        endLiveIfOver(recording);
    }

    private void wantFinalTranscript(MeetingRecording recording, String recallRecordingId) {
        if (recording.getFinalTranscriptId() == null) {
            events.publishEvent(new FinalTranscriptWanted(recording.getId(), recallRecordingId));
        }
    }

    /**
     * Once the call is over the live copilot reads nothing more, and its streamed lines go: the final transcript, or
     * nothing, is the only copy of the call kept. Every window is told, so it shows the call ended.
     */
    void endLiveIfOver(MeetingRecording recording) {
        if (!recording.isLiveCopilot()) {
            return;
        }
        if (recording.getStatus() == NotetakerStatus.PROCESSING || !recording.getStatus().isLive()) {
            liveLines.deleteByRecording(recording.getId());
        }
        copilotStream.publish(recording.getId());
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
