package app.lightmove.api.outreach.repository;

import app.lightmove.api.outreach.model.OutreachMessage;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutreachMessageRepository extends JpaRepository<OutreachMessage, UUID> {

    /** The daily cap: one sender's sends in one workspace since their local midnight. */
    long countByWorkspaceIdAndSenderUserIdAndSentAtGreaterThanEqual(UUID workspaceId, UUID senderUserId,
                                                                    Instant since);

    Optional<OutreachMessage> findFirstByWorkspaceIdAndSenderUserIdAndProviderMessageId(UUID workspaceId,
                                                                                    UUID senderUserId,
                                                                                    String providerMessageId);

    List<OutreachMessage> findByWorkspaceIdAndEnrollmentIdInOrderBySentAtAsc(UUID workspaceId,
                                                                             Collection<UUID> enrollmentIds);
}
