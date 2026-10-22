package app.lightmove.api.notetaker.repository;

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

    /** Runs a bot is sent on but has never reported from, one removed before it did included. */
    @Query("select r from MeetingRecording r where r.status in ('LAUNCHING', 'REMOVE_REQUESTED') "
            + "and r.lastHeartbeatAt is null and r.createdAt < :sentBefore")
    List<MeetingRecording> findNeverReported(Instant sentBefore);

    /** Runs whose bot reported once and then fell silent. */
    @Query("select r from MeetingRecording r where r.status in :statuses and r.lastHeartbeatAt < :heardBefore")
    List<MeetingRecording> findSilentSince(Collection<NotetakerStatus> statuses, Instant heardBefore);
}
