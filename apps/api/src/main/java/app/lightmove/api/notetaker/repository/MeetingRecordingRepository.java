package app.lightmove.api.notetaker.repository;

import app.lightmove.api.notetaker.constant.NotetakerStatus;
import app.lightmove.api.notetaker.model.MeetingRecording;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

/** Notetaker recordings. Every read a caller reaches carries the workspace; the webhook's keys on Recall's bot id. */
public interface MeetingRecordingRepository extends JpaRepository<MeetingRecording, UUID> {

    List<MeetingRecording> findByWorkspaceIdAndPersonIdOrderByStartsAtDescCreatedAtDesc(UUID workspaceId,
                                                                                    UUID personId);

    Optional<MeetingRecording> findByIdAndWorkspaceIdAndPersonId(UUID id, UUID workspaceId, UUID personId);

    Optional<MeetingRecording> findByRecallBotId(String recallBotId);

    boolean existsByWorkspaceIdAndJoinUrlAndStartsAtAndStatusIn(UUID workspaceId, String joinUrl, Instant startsAt,
                                                                Collection<NotetakerStatus> statuses);

    /** Live recordings whose call ended a while ago: the ones a lost webhook would leave hanging. */
    List<MeetingRecording> findByStatusInAndEndsAtBeforeAndRecallBotIdIsNotNullOrderByEndsAtAsc(
            Collection<NotetakerStatus> statuses, Instant endedBefore, Limit limit);
}
