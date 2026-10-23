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
 * Keeps a verbatim transcript no longer than what it was kept for: gone once its Meeting note is purged for good
 * ({@code PersonNotePurge}, which empties the row's {@code note_id}), and from a recording that never filed one once it
 * has sat untouched for the notes' own purge age.
 */
@Component
@Slf4j
public class NotetakerTranscriptPurge {

    private final MeetingRecordingRepository recordings;
    private final Clock clock;
    private final Duration purgeAfter;

    public NotetakerTranscriptPurge(MeetingRecordingRepository recordings, Clock clock,
                                    LightMoveProperties properties) {
        this.recordings = recordings;
        this.clock = clock;
        this.purgeAfter = properties.notes().purgeAfter();
    }

    @Scheduled(fixedDelayString = "${lightmove.notes.purge-interval}",
            initialDelayString = "${lightmove.notes.purge-interval}")
    public void scheduledPurge() {
        purgeAt(clock.instant());
    }

    @Transactional
    public int purgeAt(Instant now) {
        int purged = recordings.purgeTranscripts(now.minus(purgeAfter));
        if (purged > 0) {
            log.info("Notetaker: cleared {} transcripts whose notes are gone", purged);
        }
        return purged;
    }
}
