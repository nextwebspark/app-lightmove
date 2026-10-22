package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.storage.service.DocumentStore;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Deletes what a removed bot captured: Remove notetaker keeps nothing. Each file goes before its row, so a purge
 * cut short leaves a row to find again, never a file nobody knows of. The scheduler runs it every pass.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class NotetakerMediaPurge {

    private static final int RECORDINGS_PER_PASS = 20;

    private final MeetingMediaPieces pieces;
    private final DocumentStore store;

    void purgeRemoved() {
        for (UUID recordingId : pieces.removedStillHoldingPieces(RECORDINGS_PER_PASS)) {
            for (String key : pieces.storageKeysOf(recordingId)) {
                try {
                    store.delete(key);
                    pieces.forget(recordingId, key);
                } catch (IOException failed) {
                    log.warn("A removed Notetaker run's piece {} could not be deleted; next pass tries again", key,
                            failed);
                }
            }
        }
    }
}
