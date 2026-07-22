package app.lightmove.api.project.repository;

import app.lightmove.api.project.constant.ClientRepStatus;
import app.lightmove.api.project.model.ClientRepresentative;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every finder carries the workspace or client id — an unscoped lookup on tenant data must not exist. */
public interface ClientRepresentativeRepository extends JpaRepository<ClientRepresentative, UUID> {

    List<ClientRepresentative> findByClientIdOrderByCreatedAtAsc(UUID clientId);

    /** One drawer's worth of representatives, and the list-screen enrichment for a set of clients. */
    List<ClientRepresentative> findByWorkspaceIdAndClientIdIn(UUID workspaceId, List<UUID> clientIds);

    /** The portal's scoping lookup: which client does this signed-in representative belong to? */
    Optional<ClientRepresentative> findByWorkspaceIdAndUserIdAndStatus(
            UUID workspaceId, UUID userId, ClientRepStatus status);

    /** Dedupe on invite: an outstanding or revoked row for this address is reused, not duplicated. */
    Optional<ClientRepresentative> findByClientIdAndEmailIgnoreCase(UUID clientId, String email);

    /** Flip on accept: the row the redeemed invitation was issued for. */
    Optional<ClientRepresentative> findByClientIdAndEmailIgnoreCaseAndStatus(
            UUID clientId, String email, ClientRepStatus status);
}
