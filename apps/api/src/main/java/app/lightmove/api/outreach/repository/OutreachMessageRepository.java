package app.lightmove.api.outreach.repository;

import app.lightmove.api.outreach.constant.MessageDirection;
import app.lightmove.api.outreach.constant.MessageKind;
import app.lightmove.api.outreach.constant.MessageStatus;
import app.lightmove.api.outreach.model.OutreachMessage;
import app.lightmove.api.outreach.model.PersonNextSend;
import app.lightmove.api.outreach.model.PersonScheduledSender;
import app.lightmove.api.outreach.model.PersonSentTally;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutreachMessageRepository extends JpaRepository<OutreachMessage, UUID> {

    /** The person's newest reply on a position, whichever thread or run it came back in. */
    Optional<OutreachMessage> findFirstByWorkspaceIdAndProjectIdAndPersonIdAndDirectionAndKindOrderByOccurredAtDesc(
            UUID workspaceId, UUID projectId, UUID personId, MessageDirection direction, MessageKind kind);

    /** The daily cap: one sender's sends in one workspace since their local midnight. Only a sent email has a {@code sentAt}. */
    long countByWorkspaceIdAndSenderUserIdAndSentAtGreaterThanEqual(UUID workspaceId, UUID senderUserId,
                                                                    Instant since);

    /** The email a bounce names, or a reply already recorded: a provider id is unique only within one mailbox. */
    Optional<OutreachMessage> findFirstByWorkspaceIdAndSenderUserIdAndProviderMessageIdAndDirection(
            UUID workspaceId, UUID senderUserId, String providerMessageId, MessageDirection direction);

    boolean existsByWorkspaceIdAndSenderUserIdAndProviderMessageIdAndDirection(UUID workspaceId, UUID senderUserId,
                                                                              String providerMessageId,
                                                                              MessageDirection direction);

    /**
     * The latest email a mailbox sent into a thread: whose conversation the thread is. Asked by status — a line
     * still waiting carries the thread it will answer and a time ahead, and would take the thread from its run.
     */
    Optional<OutreachMessage>
            findFirstByWorkspaceIdAndSenderUserIdAndThreadIdAndDirectionAndStatusOrderByOccurredAtDesc(
            UUID workspaceId, UUID senderUserId, String threadId, MessageDirection direction, MessageStatus status);

    Optional<OutreachMessage> findFirstByWorkspaceIdAndSenderUserIdAndThreadIdAndKindOrderByCreatedAtDesc(
            UUID workspaceId, UUID senderUserId, String threadId, MessageKind kind);

    Optional<OutreachMessage> findFirstByWorkspaceIdAndSenderUserIdAndThreadIdAndDirectionAndStatusOrderByCreatedAtDesc(
            UUID workspaceId, UUID senderUserId, String threadId, MessageDirection direction, MessageStatus status);

    /** The thread a consultant last emailed an executive in on a position: where a reply from the composer goes. */
    Optional<OutreachMessage>
            findFirstByWorkspaceIdAndProjectIdAndPersonIdAndSenderUserIdAndDirectionAndStatusAndThreadIdIsNotNullOrderByOccurredAtDesc(
            UUID workspaceId, UUID projectId, UUID personId, UUID senderUserId, MessageDirection direction,
            MessageStatus status);

    /** The thread's latest message the provider knows — what a reply answers. A bounce is a mail server's, never it. */
    Optional<OutreachMessage>
            findFirstByWorkspaceIdAndSenderUserIdAndThreadIdAndProviderMessageIdIsNotNullAndKindNotOrderByOccurredAtDesc(
            UUID workspaceId, UUID senderUserId, String threadId, MessageKind kind);

    /** The thread's first email: its subject is the one a reply keeps. */
    Optional<OutreachMessage> findFirstByWorkspaceIdAndSenderUserIdAndThreadIdAndDirectionAndSubjectIsNotNullOrderByOccurredAtAsc(
            UUID workspaceId, UUID senderUserId, String threadId, MessageDirection direction);

    long countByWorkspaceIdAndSenderUserIdAndKindAndStatus(UUID workspaceId, UUID senderUserId, MessageKind kind,
                                                           MessageStatus status);

    /** A claim that outlived its dispatcher. A system read across workspaces, as the claim itself is. */
    List<OutreachMessage> findByStatusAndSendingSinceBefore(MessageStatus status, Instant cutoff);

    List<OutreachMessage> findByProjectIdAndStatus(UUID projectId, MessageStatus status);

    Optional<OutreachMessage> findByIdAndWorkspaceIdAndProjectId(UUID id, UUID workspaceId, UUID projectId);

    /** A line of {@code kind} in the thread no read has completed yet, oldest first. */
    Optional<OutreachMessage>
            findFirstByWorkspaceIdAndSenderUserIdAndThreadIdAndKindAndProviderMessageIdIsNullOrderByOccurredAtAsc(
            UUID workspaceId, UUID senderUserId, String threadId, MessageKind kind);

    /**
     * Every thread an email went into since {@code since}, each by its latest send. A system read across workspaces:
     * the poll that listens for replies; each row it answers carries its own workspace.
     */
    @Query(nativeQuery = true, value = """
            SELECT DISTINCT ON (workspace_id, sender_user_id, thread_id) * FROM app_lm_outreach_message
            WHERE direction = 'OUTBOUND' AND status = 'SENT' AND thread_id IS NOT NULL AND occurred_at >= :since
            ORDER BY workspace_id, sender_user_id, thread_id, occurred_at DESC
            """)
    List<OutreachMessage> findLatestSendOfEachThreadSince(Instant since);

    /** Where a read of the thread may start: the latest line that came back, else the first that went. */
    @Query("""
            select coalesce(max(case when m.direction = app.lightmove.api.outreach.constant.MessageDirection.INBOUND
                                     then m.occurredAt end),
                            min(m.occurredAt))
            from OutreachMessage m
            where m.workspaceId = :workspaceId and m.senderUserId = :senderUserId and m.threadId = :threadId
            """)
    Instant findThreadReadFrom(UUID workspaceId, UUID senderUserId, String threadId);

    /** A run's steps as they went. Asked by kind: the table holds more of a conversation than its runs. */
    List<OutreachMessage> findByWorkspaceIdAndEnrollmentIdInAndKindOrderBySentAtAsc(UUID workspaceId,
                                                                                    Collection<UUID> enrollmentIds,
                                                                                    MessageKind kind);

    /** One executive's lines on one position, newest first. A cancelled send is no part of the conversation. */
    @Query("""
            select m from OutreachMessage m
            where m.workspaceId = :workspaceId and m.projectId = :projectId and m.personId = :personId
              and m.status <> app.lightmove.api.outreach.constant.MessageStatus.CANCELLED
            order by m.occurredAt desc, m.id desc
            """)
    List<OutreachMessage> findConversation(UUID workspaceId, UUID projectId, UUID personId, Limit limit);

    /** Each executive's latest line on a position, for the queue's preview: what happened, never what is due. */
    @Query(nativeQuery = true, value = """
            SELECT DISTINCT ON (person_id) * FROM app_lm_outreach_message
            WHERE workspace_id = :workspaceId AND project_id = :projectId
              AND status NOT IN ('CANCELLED', 'SCHEDULED')
            ORDER BY person_id, occurred_at DESC, id DESC
            """)
    List<OutreachMessage> findLatestOfEachPerson(UUID workspaceId, UUID projectId);

    @Query("""
            select m.personId as personId, min(m.sentAt) as firstSentAt, max(m.sentAt) as lastSentAt
            from OutreachMessage m
            where m.workspaceId = :workspaceId and m.projectId = :projectId and m.sentAt is not null
            group by m.personId
            """)
    List<PersonSentTally> tallySentByPerson(UUID workspaceId, UUID projectId);

    @Query("""
            select m.personId as personId, min(m.sendAt) as sendAt from OutreachMessage m
            where m.workspaceId = :workspaceId and m.projectId = :projectId
              and m.status = app.lightmove.api.outreach.constant.MessageStatus.SCHEDULED
            group by m.personId
            """)
    List<PersonNextSend> findNextSendOfEachPerson(UUID workspaceId, UUID projectId);

    /** Who has an own email waiting to go to each executive on a position: it makes the conversation theirs too. */
    @Query("""
            select distinct m.personId as personId, m.senderUserId as senderUserId from OutreachMessage m
            where m.workspaceId = :workspaceId and m.projectId = :projectId
              and m.status = app.lightmove.api.outreach.constant.MessageStatus.SCHEDULED
            """)
    List<PersonScheduledSender> findScheduledSendersOfEachPerson(UUID workspaceId, UUID projectId);

    /** When the first email went to one executive on a position, or null before any has. */
    @Query("""
            select min(m.sentAt) from OutreachMessage m
            where m.workspaceId = :workspaceId and m.projectId = :projectId and m.personId = :personId
            """)
    Instant findFirstSentAt(UUID workspaceId, UUID projectId, UUID personId);

    /** Who on a position has a line whose subject or text matches {@code pattern}, a lower-cased LIKE pattern. */
    @Query("""
            select distinct m.personId from OutreachMessage m
            where m.workspaceId = :workspaceId and m.projectId = :projectId
              and m.status <> app.lightmove.api.outreach.constant.MessageStatus.CANCELLED
              and (lower(m.subject) like :pattern escape '\\' or lower(m.bodyText) like :pattern escape '\\')
            """)
    List<UUID> findPersonIdsMatching(UUID workspaceId, UUID projectId, String pattern);
}
