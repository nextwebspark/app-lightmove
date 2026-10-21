package app.lightmove.api.outreach.repository;

import app.lightmove.api.outreach.model.OutreachMessageAttachment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutreachMessageAttachmentRepository extends JpaRepository<OutreachMessageAttachment, UUID> {

    List<OutreachMessageAttachment> findByMessageIdOrderByPosition(UUID messageId);

    /** A conversation's lines' files in one read, never one per line. */
    List<OutreachMessageAttachment> findByWorkspaceIdAndMessageIdInOrderByPosition(UUID workspaceId,
                                                                                   Collection<UUID> messageIds);

    Optional<OutreachMessageAttachment> findByIdAndMessageIdAndWorkspaceId(UUID id, UUID messageId, UUID workspaceId);
}
