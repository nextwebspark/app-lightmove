package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.notetaker.repository.MeetingRecordingRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Clears a transcript only where a deployment sets {@code lightmove.recall.transcript-retention}: one whose Meeting note
 * was purged for good, or whose recording never filed one, once it has sat untouched that long. Unset, nothing is cleared.
 */
@Component
@Slf4j
public class NotetakerTranscriptPurge {

    private final MeetingRecordingRepository recordings;
    private final Clock clock;
    private final Duration retention;

    public NotetakerTranscriptPurge(MeetingRecordingRepository recordings, Clock clock,
                                    LightMoveProperties properties) {
        this.recordings = recordings;
        this.clock = clock;
        this.retention = properties.recall().transcriptRetention();
    }

    @Scheduled(fixedDelayString = "${lightmove.notes.purge-interval}",
            initialDelayString = "${lightmove.notes.purge-interval}")
    public void scheduledPurge() {
        purgeAt(clock.instant());
    }

    @Transactional
    public int purgeAt(Instant now) {
        return retention == null ? 0 : purgeUntouchedSince(now.minus(retention));
    }

    @Transactional
    public int purgeUntouchedSince(Instant cutoff) {
        int purged = recordings.purgeTranscripts(cutoff);
        if (purged > 0) {
            log.info("Notetaker: cleared {} transcripts past their retention", purged);
        }
        return purged;
    }
}
