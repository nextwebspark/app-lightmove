package app.lightmove.api.project.repository;

import app.lightmove.api.project.model.Client;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** Every finder carries the workspace id — an unscoped lookup on tenant data must not exist. */
public interface ClientRepository extends JpaRepository<Client, UUID> {

    List<Client> findByWorkspaceIdOrderByNameAsc(UUID workspaceId);

    List<Client> findByWorkspaceIdAndArchivedAtIsNullOrderByNameAsc(UUID workspaceId);

    /** Held to commit: an archive and a position opened under the record queue, so neither misses the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Client c where c.id = :id and c.workspaceId = :workspaceId")
    Optional<Client> lockToArchive(UUID id, UUID workspaceId);

    /** {@link #lockToArchive}'s other half, shared: positions opened under one record at once never wait. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select c from Client c where c.id = :id and c.workspaceId = :workspaceId")
    Optional<Client> lockToOpenPositionUnder(UUID id, UUID workspaceId);

    Optional<Client> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<Client> findByWorkspaceIdAndNameIgnoreCase(UUID workspaceId, String name);

    List<Client> findByWorkspaceIdAndCompanySourceAndCompanySourceId(UUID workspaceId, String companySource,
                                                                     String companySourceId);
}
