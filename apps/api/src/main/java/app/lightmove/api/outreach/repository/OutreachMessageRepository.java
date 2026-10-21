package app.lightmove.api.outreach.repository;

import app.lightmove.api.outreach.constant.MessageKind;
import app.lightmove.api.outreach.model.OutreachMessage;
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

    /** The daily cap: one sender's sends in one workspace since their local midnight. Only a sent email has a {@code sentAt}. */
    long countByWorkspaceIdAndSenderUserIdAndSentAtGreaterThanEqual(UUID workspaceId, UUID senderUserId,
                                                                    Instant since);

    Optional<OutreachMessage> findFirstByWorkspaceIdAndSenderUserIdAndProviderMessageId(UUID workspaceId,
                                                                                    UUID senderUserId,
                                                                                    String providerMessageId);

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

    /** Each executive's latest line on a position, for the queue's preview. */
    @Query(nativeQuery = true, value = """
            SELECT DISTINCT ON (person_id) * FROM app_lm_outreach_message
            WHERE workspace_id = :workspaceId AND project_id = :projectId AND status <> 'CANCELLED'
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
