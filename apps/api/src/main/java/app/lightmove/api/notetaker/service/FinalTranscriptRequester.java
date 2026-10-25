package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.notetaker.model.FinalTranscriptWanted;
import app.lightmove.api.notetaker.model.MeetingRecording;
import app.lightmove.api.notetaker.repository.MeetingRecordingRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Asks Recall for a live copilot call's accurate transcript once the call is over. The bot's own transcript is the
 * low-latency stream, English only, so the note, Suggest changes and Assess with AI never read it. One request at a
 * time per call, by claim; a refused one is asked again by the next delivery or by {@link NotetakerReconcile}.
 */
@Component
@Slf4j
public class FinalTranscriptRequester {

    static final Duration CLAIM_LIFETIME = Duration.ofMinutes(10);

    private final RecallBotApi recall;
    private final MeetingRecordingRepository recordings;
    private final TransactionTemplate transactions;
    private final Clock clock;

    FinalTranscriptRequester(RecallBotApi recall, MeetingRecordingRepository recordings,
                             PlatformTransactionManager transactionManager, Clock clock) {
        this.recall = recall;
        this.recordings = recordings;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWanted(FinalTranscriptWanted wanted) {
        request(wanted.recordingId(), wanted.recallRecordingId());
    }

    void request(UUID recordingId, String recallRecordingId) {
        Instant now = clock.instant();
        Integer claimed = transactions.execute(status ->
                recordings.claimFinalTranscript(recordingId, now, now.minus(CLAIM_LIFETIME)));
        if (claimed == null || claimed == 0) {
            return;
        }
        String botId = transactions.execute(status ->
                recordings.findById(recordingId).map(MeetingRecording::getRecallBotId).orElse(null));
        try {
            String recording = recallRecordingId != null ? recallRecordingId
                    : botId == null ? null : recall.state(botId).recordingId();
            if (recording == null) {
                release(recordingId);
                return;
            }
            String transcriptId = recall.requestTranscript(recording);
            transactions.executeWithoutResult(status -> recordings.findById(recordingId)
                    .ifPresent(held -> held.finalTranscriptAsked(transcriptId)));
            log.info("Notetaker recording {}: asked Recall for its final transcript {}", recordingId, transcriptId);
        } catch (VendorException refused) {
            log.warn("Notetaker recording {}: Recall refused its final transcript: {}", recordingId,
                    refused.getMessage());
            release(recordingId);
        }
    }

    private void release(UUID recordingId) {
        transactions.executeWithoutResult(status -> recordings.releaseFinalTranscript(recordingId));
    }
}
