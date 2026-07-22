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

    /**
     * A client representative's outstanding invitation. Scoped by client id too, so re-inviting the same
     * address for a client refreshes that invitation without colliding with an unrelated staff invite to
     * the same email.
     */
    Optional<Invitation> findByWorkspaceIdAndClientIdAndEmailAndStatus(
            UUID workspaceId, UUID clientId, String email, InvitationStatus status);

    /**
     * The caller's own outstanding invitation, for the server-derived invitee routing on {@code /me}
     * and the token-less accept. Most recent first, because a person can hold several dead invitations
     * from workspaces that since re-invited or gave up.
     */
    Optional<Invitation> findFirstByEmailAndStatusOrderByCreatedAtDesc(String email, InvitationStatus status);
}
