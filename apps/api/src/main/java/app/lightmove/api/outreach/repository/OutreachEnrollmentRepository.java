package app.lightmove.api.outreach.repository;

import app.lightmove.api.outreach.constant.EnrollmentStatus;
import app.lightmove.api.outreach.constant.MailboxGatewayKind;
import app.lightmove.api.outreach.model.OutreachEnrollment;
import app.lightmove.api.outreach.model.OutreachRunTally;
import app.lightmove.api.outreach.model.SenderLiveRunCount;
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

    /** One sender's live runs, whichever gateway made their threads: each stops at its next send without a mailbox. */
    @Query("select count(distinct e.sequenceId) as sequences, count(distinct e.personId) as people "
            + "from OutreachEnrollment e where e.workspaceId = :workspaceId and e.senderUserId = :senderUserId "
            + "and e.status in (app.lightmove.api.outreach.constant.EnrollmentStatus.SCHEDULED, "
            + "app.lightmove.api.outreach.constant.EnrollmentStatus.ACTIVE)")
    SenderLiveRunCount countLiveRunsOf(UUID workspaceId, UUID senderUserId);

    /** One sender's runs still to send in a thread {@code gateway} made: what a move off it stops. */
    @Query("select count(e) from OutreachEnrollment e where e.workspaceId = :workspaceId "
            + "and e.senderUserId = :senderUserId and e.threadGateway = :gateway "
            + "and e.status = app.lightmove.api.outreach.constant.EnrollmentStatus.ACTIVE")
    long countRunningThreadsOf(UUID workspaceId, UUID senderUserId, MailboxGatewayKind gateway);

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
