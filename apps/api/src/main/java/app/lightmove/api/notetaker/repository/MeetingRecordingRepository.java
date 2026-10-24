package app.lightmove.api.notetaker.repository;

import app.lightmove.api.notetaker.constant.NotetakerStatus;
import app.lightmove.api.notetaker.model.MeetingRecording;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /** The row a Suggest changes decision writes, locked first so two tabs' decisions queue rather than collide. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from MeetingRecording r where r.id = :id and r.workspaceId = :workspaceId "
            + "and r.projectId = :projectId and r.personId = :personId")
    Optional<MeetingRecording> lockForDecision(UUID id, UUID workspaceId, UUID projectId, UUID personId);

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
     * One coach run at a time per live call: lines arriving while a run asks the model wait for the next. A claim
     * older than {@code staleBefore} belonged to a run that died with its instance.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE app_lm_meeting_recording SET coaching_since = :now
            WHERE id = :id AND live_copilot AND live_status = 'LISTENING'
              AND (coaching_since IS NULL OR coaching_since < :staleBefore)
            """)
    int claimCoaching(UUID id, Instant now, Instant staleBefore);

    @Modifying
    @Query(nativeQuery = true, value = "UPDATE app_lm_meeting_recording SET coaching_since = NULL WHERE id = :id")
    int releaseCoaching(UUID id);

    /**
     * A transcript nothing needs any more — its note purged for good, or never filed — once untouched since
     * {@code cutoff}. The suggestions and the live copilot's reading quote the executive, so they go with it.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE app_lm_meeting_recording SET transcript = NULL, suggestions = NULL, live = NULL
            WHERE transcript IS NOT NULL AND updated_at < :cutoff
              AND ((status = 'DONE' AND note_id IS NULL) OR status IN ('FAILED', 'CANCELLED'))
            """)
    int purgeTranscripts(Instant cutoff);
}
