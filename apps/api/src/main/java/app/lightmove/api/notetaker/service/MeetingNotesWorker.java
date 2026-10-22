package app.lightmove.api.notetaker.service;

import app.lightmove.api.notetaker.model.MeetingRecordingCompleted;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs {@link MeetingNotesProcessor} off the request and scheduler threads: at once when a bot leaves, and for each
 * run the scheduler claimed — a call still going whose audio waits, or a note not yet written. The executor is the
 * application's, on virtual threads, so a run waiting here for one of the four model slots holds no pool thread.
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
        workOn(completed.recordingId());
    }

    /** Claims the run first: Try again, and a bot that just left. */
    @Async
    void workOn(UUID recordingId) {
        runs.acquireUninterruptibly();
        try {
            processor.work(recordingId);
        } finally {
            runs.release();
        }
    }

    /** A run the scheduler already claimed at {@code claim}. */
    @Async
    void workOnClaimed(UUID recordingId, Instant claim) {
        runs.acquireUninterruptibly();
        try {
            processor.workClaimed(recordingId, claim);
        } finally {
            runs.release();
        }
    }
}
