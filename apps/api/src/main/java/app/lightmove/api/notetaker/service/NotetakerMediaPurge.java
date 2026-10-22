package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.storage.service.DocumentStore;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Deletes what nobody will read: a removed bot's pieces and transcript at once (Remove notetaker keeps nothing), a
 * finished one's captions once its note is written, a failed one's after
 * {@code lightmove.notetaker.failed-media-retention}, and any piece at all once it is
 * {@code lightmove.notetaker.media-max-age} (a day) old. A call's transcript goes once its note has been purged. Each
 * file goes before its row, so a purge cut short leaves a row to find again, never a file nobody knows of. The
 * scheduler runs it every pass.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class NotetakerMediaPurge {

    private static final int RECORDINGS_PER_PASS = 20;
    private static final int OLD_PIECES_PER_PASS = 200;

    private final MeetingMediaPieces pieces;
    private final DocumentStore store;
    private final LightMoveProperties properties;
    private final MeetingTranscriptPieces transcripts;

    void purge(Instant now) {
        Instant failedBefore = now.minus(properties.notetaker().failedMediaRetention());
        transcripts.forgetUnwanted(failedBefore);
        transcripts.forgetWhereNoteIsGone();
        for (UUID recordingId : pieces.unwantedStillHoldingPieces(failedBefore, RECORDINGS_PER_PASS)) {
            for (String key : pieces.storageKeysOf(recordingId)) {
                try {
                    store.delete(key);
                    pieces.forget(recordingId, key);
                } catch (IOException failed) {
                    log.warn("An unwanted Notetaker run's piece {} could not be deleted; next pass tries again", key,
                            failed);
                }
            }
        }
        Instant receivedBefore = now.minus(properties.notetaker().mediaMaxAge());
        for (MeetingMediaPieces.StoredPiece piece : pieces.receivedBefore(receivedBefore, OLD_PIECES_PER_PASS)) {
            // Audio no pass could hear before its day was up is a gap the note owns up to, as a given-up piece is.
            if (piece.unheard()) {
                transcripts.storeSkipped(piece.recordingId(), piece.sequence(), piece.startedAt());
            }
            try {
                store.delete(piece.storageKey());
                pieces.forget(piece.recordingId(), piece.storageKey());
            } catch (IOException failed) {
                log.warn("A Notetaker piece {} past its age could not be deleted; next pass tries again",
                        piece.storageKey(), failed);
            }
        }
    }
}
