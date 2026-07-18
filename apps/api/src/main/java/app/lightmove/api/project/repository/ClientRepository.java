package app.lightmove.api.project.repository;

import app.lightmove.api.project.model.Client;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every finder carries the workspace id — an unscoped lookup on tenant data must not exist. */
public interface ClientRepository extends JpaRepository<Client, UUID> {

    List<Client> findByWorkspaceIdOrderByNameAsc(UUID workspaceId);

    Optional<Client> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<Client> findByWorkspaceIdAndNameIgnoreCase(UUID workspaceId, String name);
}
