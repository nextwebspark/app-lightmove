package app.lightmove.api.notetaker.service;

import app.lightmove.api.notetaker.model.MeetingRecordingCompleted;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs {@link MeetingNotesProcessor} off the request and scheduler threads: at once when a bot leaves, and for each
 * run the scheduler finds due — a call still going whose audio waits, or a note not yet written.
 */
@Component
@RequiredArgsConstructor
class MeetingNotesWorker {

    private static final int CONCURRENT_RUNS = 4;

    private final MeetingNotesProcessor processor;
    private final Semaphore runs = new Semaphore(CONCURRENT_RUNS);

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onLeft(MeetingRecordingCompleted completed) {
        work(completed.recordingId());
    }

    @Async
    void workOn(UUID recordingId) {
        work(recordingId);
    }

    private void work(UUID recordingId) {
        runs.acquireUninterruptibly();
        try {
            processor.work(recordingId);
        } finally {
            runs.release();
        }
    }
}
