package app.lightmove.api.workspace.repository;

import app.lightmove.api.workspace.model.Invitation;
import app.lightmove.api.workspace.constant.InvitationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    Optional<Invitation> findByTokenHash(String tokenHash);

    /** Backs the partial unique index: re-inviting someone should resend, not duplicate. */
    Optional<Invitation> findByWorkspaceIdAndEmailAndStatus(UUID workspaceId, String email, InvitationStatus status);

    List<Invitation> findByWorkspaceIdAndStatus(UUID workspaceId, InvitationStatus status);
}
