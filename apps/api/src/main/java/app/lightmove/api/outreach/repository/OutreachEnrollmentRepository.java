package app.lightmove.api.outreach.repository;

import app.lightmove.api.outreach.constant.EnrollmentStatus;
import app.lightmove.api.outreach.model.OutreachEnrollment;
import app.lightmove.api.outreach.model.SequenceEnrollmentCount;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutreachEnrollmentRepository extends JpaRepository<OutreachEnrollment, UUID> {

    List<OutreachEnrollment> findByProjectIdAndPersonIdInAndStatusIn(UUID projectId, Collection<UUID> personIds,
                                                                     Collection<EnrollmentStatus> statuses);

    boolean existsBySequenceId(UUID sequenceId);

    @Query("select e.sequenceId as sequenceId, count(e) as total, "
            + "sum(e.nextStep) as sent, "
            + "sum(case when e.status = app.lightmove.api.outreach.constant.EnrollmentStatus.REPLIED then 1 else 0 end) "
            + "as replied "
            + "from OutreachEnrollment e where e.projectId = :projectId group by e.sequenceId")
    List<SequenceEnrollmentCount> countBySequenceOfProject(UUID projectId);

    Optional<OutreachEnrollment> findByIdAndWorkspaceIdAndProjectId(UUID id, UUID workspaceId, UUID projectId);

    List<OutreachEnrollment> findByWorkspaceIdAndProjectIdOrderByEnrolledAtDesc(UUID workspaceId, UUID projectId);

    List<OutreachEnrollment> findByWorkspaceIdAndProjectIdAndPersonIdOrderByEnrolledAtDesc(UUID workspaceId,
                                                                                         UUID projectId, UUID personId);

    /** Keyed on the sender's grant as well as the thread: a thread id is only unique within one mailbox. */
    List<OutreachEnrollment> findByWorkspaceIdAndSenderUserIdAndThreadId(UUID workspaceId, UUID senderUserId,
                                                                         String threadId);

    List<OutreachEnrollment> findBySendingSinceBefore(Instant claimedBefore);

    /** Threads the reply poll still listens to: anything sent recently that has not been answered or stopped. */
    @Query("select e from OutreachEnrollment e where e.threadId is not null and e.lastSentAt >= :since "
            + "and e.status in :statuses")
    List<OutreachEnrollment> findListeningSince(Instant since, Collection<EnrollmentStatus> statuses);
}
