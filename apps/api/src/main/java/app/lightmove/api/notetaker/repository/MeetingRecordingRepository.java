package app.lightmove.api.notetaker.repository;

import app.lightmove.api.notetaker.constant.NotetakerStatus;
import app.lightmove.api.notetaker.model.MeetingRecording;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Notetaker recordings. Every read a caller reaches carries the workspace and the position the bot was sent from, so
 * one position never reads another's call; the webhook's keys on Recall's bot id.
 */
public interface MeetingRecordingRepository extends JpaRepository<MeetingRecording, UUID> {

    List<MeetingRecording> findByWorkspaceIdAndProjectIdAndPersonIdOrderByStartsAtDescCreatedAtDesc(
            UUID workspaceId, UUID projectId, UUID personId);

    Optional<MeetingRecording> findByIdAndWorkspaceIdAndProjectIdAndPersonId(UUID id, UUID workspaceId,
                                                                             UUID projectId, UUID personId);

    Optional<MeetingRecording> findByRecallBotId(String recallBotId);

    boolean existsByWorkspaceIdAndJoinUrlAndStartsAtAndStatusIn(UUID workspaceId, String joinUrl, Instant startsAt,
                                                                Collection<NotetakerStatus> statuses);

    /**
     * The worker's claim on a recording it is about to summarise: one worker per transcript, however many times it
     * was handed over. A claim older than {@code staleBefore} belonged to a worker that died with its instance.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE app_lm_meeting_recording SET processing_since = :now
            WHERE id = :id AND status = 'PROCESSING' AND note_id IS NULL
              AND (processing_since IS NULL OR processing_since < :staleBefore)
            """)
    int claimProcessing(UUID id, Instant now, Instant staleBefore);

    @Modifying
    @Query(nativeQuery = true, value = "UPDATE app_lm_meeting_recording SET processing_since = NULL WHERE id = :id")
    int releaseProcessing(UUID id);

    /**
     * One press of Suggest changes at a time per call: the model is asked once however many tabs press together. A
     * claim older than {@code staleBefore} belonged to a request that died with its instance.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE app_lm_meeting_recording SET suggesting_since = :now
            WHERE id = :id AND status = 'DONE' AND transcript IS NOT NULL
              AND (suggesting_since IS NULL OR suggesting_since < :staleBefore)
            """)
    int claimSuggesting(UUID id, Instant now, Instant staleBefore);

    @Modifying
    @Query(nativeQuery = true, value = "UPDATE app_lm_meeting_recording SET suggesting_since = NULL WHERE id = :id")
    int releaseSuggesting(UUID id);

    /**
     * A transcript outlives nothing it was kept for: cleared once its note is gone for good, or once a recording that
     * never filed one has sat untouched past the notes' own purge age. The suggestions read off it quote the
     * executive, so they go with it.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE app_lm_meeting_recording SET transcript = NULL, suggestions = NULL
            WHERE transcript IS NOT NULL
              AND ((status = 'DONE' AND note_id IS NULL)
                   OR (status IN ('FAILED', 'CANCELLED') AND updated_at < :cutoff))
            """)
    int purgeTranscripts(Instant cutoff);
}
