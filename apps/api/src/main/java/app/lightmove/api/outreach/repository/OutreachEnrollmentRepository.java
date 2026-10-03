package app.lightmove.api.outreach.repository;

import app.lightmove.api.outreach.constant.EnrollmentStatus;
import app.lightmove.api.outreach.model.OutreachEnrollment;
import app.lightmove.api.outreach.model.OutreachRunTally;
import app.lightmove.api.outreach.model.SequenceEnrollmentCount;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
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

    @Query("select e.status as status, count(e) as total, sum(e.nextStep) as sent, "
            + "sum(case when e.nextStep > 0 then 1 else 0 end) as reached, min(e.nextSendAt) as nextSendAt "
            + "from OutreachEnrollment e where e.workspaceId = :workspaceId and e.projectId = :projectId "
            + "group by e.status")
    List<OutreachRunTally> tallyByStatus(UUID workspaceId, UUID projectId);

    List<OutreachEnrollment> findByWorkspaceIdAndProjectIdOrderByEnrolledAtDesc(UUID workspaceId, UUID projectId,
                                                                               Pageable page);

    List<OutreachEnrollment> findByWorkspaceIdAndProjectIdAndPersonIdOrderByEnrolledAtDesc(UUID workspaceId,
                                                                                         UUID projectId, UUID personId);

    /**
     * One sender's runs to these addresses (lower-cased), across the workspace's positions: who a booking
     * through their link can be — someone they actually emailed, at the address they emailed.
     */
    @Query("select e from OutreachEnrollment e where e.workspaceId = :workspaceId and e.senderUserId = :senderUserId "
            + "and lower(e.toAddress) in :addresses")
    List<OutreachEnrollment> findEmailedBy(UUID workspaceId, UUID senderUserId, Collection<String> addresses);

    /** Keyed on the sender's grant as well as the thread: a thread id is only unique within one mailbox. */
    List<OutreachEnrollment> findByWorkspaceIdAndSenderUserIdAndThreadId(UUID workspaceId, UUID senderUserId,
                                                                         String threadId);

    /** One sender's runs that have sent and may send again: what a move to another gateway stops. */
    @Query("select e from OutreachEnrollment e where e.workspaceId = :workspaceId and e.senderUserId = :senderUserId "
            + "and e.status = app.lightmove.api.outreach.constant.EnrollmentStatus.ACTIVE and e.threadId is not null")
    List<OutreachEnrollment> findRunningThreadsOf(UUID workspaceId, UUID senderUserId);

    /**
     * Claims older than a send could take, on runs still due to send. A system job's read, so across
     * workspaces by design; no request path may use it.
     */
    List<OutreachEnrollment> findByStatusInAndSendingSinceBefore(Collection<EnrollmentStatus> statuses,
                                                                 Instant claimedBefore);

    /**
     * Threads the reply poll still listens to: anything sent recently that has not been answered or
     * stopped. A system job's read, so across workspaces by design; no request path may use it.
     */
    @Query("select e from OutreachEnrollment e where e.threadId is not null and e.lastSentAt >= :since "
            + "and e.status in :statuses")
    List<OutreachEnrollment> findListeningSince(Instant since, Collection<EnrollmentStatus> statuses);
}
