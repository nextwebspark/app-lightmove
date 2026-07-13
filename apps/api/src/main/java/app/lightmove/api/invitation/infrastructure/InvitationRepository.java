package app.lightmove.api.invitation.infrastructure;

import app.lightmove.api.invitation.domain.Invitation;
import app.lightmove.api.invitation.domain.InvitationStatus;
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
