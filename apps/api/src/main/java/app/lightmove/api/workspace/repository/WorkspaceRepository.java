package app.lightmove.api.workspace.repository;

import app.lightmove.api.workspace.model.Workspace;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

    boolean existsBySlug(String slug);

    /** Held against the purge's claim, which takes the same row {@code FOR UPDATE}: a restore and a purge never race. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Workspace w WHERE w.id = :id")
    Optional<Workspace> lockById(UUID id);
}
