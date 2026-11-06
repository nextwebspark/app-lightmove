package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.tenant.EveryWorkspace;
import app.lightmove.api.notetaker.repository.MeetingRecordingRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Clears a transcript only where a deployment sets {@code lightmove.recall.transcript-retention}: one whose Meeting note
 * was purged for good, or whose recording never filed one, once it has sat untouched that long. Unset, nothing is cleared.
 */
@Component
@Slf4j
public class NotetakerTranscriptPurge {

    private final MeetingRecordingRepository recordings;
    private final EveryWorkspace workspaces;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final Duration retention;

    public NotetakerTranscriptPurge(MeetingRecordingRepository recordings, EveryWorkspace workspaces,
                                    TransactionTemplate transactions, Clock clock, LightMoveProperties properties) {
        this.recordings = recordings;
        this.workspaces = workspaces;
        this.transactions = transactions;
        this.clock = clock;
        this.retention = properties.recall().transcriptRetention();
    }

    @Scheduled(fixedDelayString = "${lightmove.notes.purge-interval}",
            initialDelayString = "${lightmove.notes.purge-interval}")
    public void scheduledPurge() {
        purgeAt(clock.instant());
    }

    public int purgeAt(Instant now) {
        return retention == null ? 0 : purgeUntouchedSince(now.minus(retention));
    }

    /** A workspace at a time: the recordings, and the notes a transcript is weighed against, are fenced. */
    public int purgeUntouchedSince(Instant cutoff) {
        int purged = workspaces.sum(workspaceId -> {
            Integer cleared = transactions.execute(status -> recordings.purgeTranscripts(cutoff));
            return cleared == null ? 0 : cleared;
        });
        if (purged > 0) {
            log.info("Notetaker: cleared {} transcripts past their retention", purged);
        }
        return purged;
    }
}
