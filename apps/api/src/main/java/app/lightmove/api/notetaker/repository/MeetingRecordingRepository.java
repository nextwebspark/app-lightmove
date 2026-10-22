package app.lightmove.api.notetaker.repository;

import app.lightmove.api.notetaker.constant.MeetingRecorderKind;
import app.lightmove.api.notetaker.constant.NotetakerStatus;
import app.lightmove.api.notetaker.model.MeetingRecording;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Recordings. A staff read carries the workspace; the bot's calls are keyed on the run id and its token. */
public interface MeetingRecordingRepository extends JpaRepository<MeetingRecording, UUID> {

    Optional<MeetingRecording> findByWorkspaceIdAndCallKey(UUID workspaceId, String callKey);

    List<MeetingRecording> findByWorkspaceIdAndCallKeyIn(UUID workspaceId, Collection<String> callKeys);

    /** A person's calls whose note is filed, whatever became of the meeting rows they were found through. */
    List<MeetingRecording> findByWorkspaceIdAndPersonIdAndNoteIdIsNotNull(UUID workspaceId, UUID personId);

    /**
     * Runs our own bot is sent on but has never reported from, one removed before it did included. A backup's bot
     * reports by webhook and on its own clock, so it is never swept as ours is.
     */
    @Query("select r from MeetingRecording r where r.recorder = 'OWN' "
            + "and r.status in ('LAUNCHING', 'REMOVE_REQUESTED') "
            + "and r.lastHeartbeatAt is null and r.createdAt < :sentBefore")
    List<MeetingRecording> findNeverReported(Instant sentBefore);

    /** Runs whose bot of ours reported once and then fell silent. */
    @Query("select r from MeetingRecording r where r.recorder = 'OWN' and r.status in :statuses "
            + "and r.lastHeartbeatAt < :heardBefore")
    List<MeetingRecording> findSilentSince(Collection<NotetakerStatus> statuses, Instant heardBefore);

    /** Backup runs still in the call long after it was due to end: their bot was never heard from again. */
    @Query("select r from MeetingRecording r where r.recorder = 'RECALL' and r.status in "
            + "('LAUNCHING', 'WAITING_ROOM', 'RECORDING', 'REMOVE_REQUESTED') and r.endsAt < :endedBefore")
    List<MeetingRecording> findBackupsLostSince(Instant endedBefore);

    Optional<MeetingRecording> findByRecorderAndExternalId(MeetingRecorderKind recorder, String externalId);
}
