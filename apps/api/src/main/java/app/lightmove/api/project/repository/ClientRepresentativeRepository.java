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

    /** Resolve a representative by id within the workspace — for attaching them to a mandate. */
    Optional<ClientRepresentative> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    /** Dedupe on invite: an outstanding or revoked row for this address is reused, not duplicated. */
    Optional<ClientRepresentative> findByClientIdAndEmailIgnoreCase(UUID clientId, String email);

    /** Flip on accept: the row the redeemed invitation was issued for. */
    Optional<ClientRepresentative> findByClientIdAndEmailIgnoreCaseAndStatus(
            UUID clientId, String email, ClientRepStatus status);
}
