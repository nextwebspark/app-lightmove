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

    /**
     * Staff only: client invites share {@code (workspace_id, email)}, and a staff re-invite refreshing
     * one would land the acceptor as a CLIENT, or 500 on a non-unique result.
     */
    Optional<Invitation> findByWorkspaceIdAndEmailAndClientIdIsNullAndStatus(
            UUID workspaceId, String email, InvitationStatus status);

    List<Invitation> findByWorkspaceIdAndStatus(UUID workspaceId, InvitationStatus status);

    /** Client-rep invites never surface on Members. */
    List<Invitation> findByWorkspaceIdAndClientIdIsNullAndStatus(UUID workspaceId, InvitationStatus status);

    /** Scoped by client too, so it never collides with a staff invite to the same email. */
    Optional<Invitation> findByWorkspaceIdAndClientIdAndEmailAndStatus(
            UUID workspaceId, UUID clientId, String email, InvitationStatus status);

    /** For {@code /me}; a person may be invited to several workspaces at once. Newest first. */
    List<Invitation> findByEmailAndStatusOrderByCreatedAtDesc(String email, InvitationStatus status);
}
