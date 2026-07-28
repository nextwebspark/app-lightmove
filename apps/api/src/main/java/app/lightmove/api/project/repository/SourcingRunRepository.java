package app.lightmove.api.project.repository;

import app.lightmove.api.project.model.SourcingRun;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The one-per-project sourcing run. Tenant scoping happens in the service, which resolves the
 * project through {@code (id, workspaceId)} before ever touching a run — the same order
 * {@code SourcingService.requireProject} established.
 */
public interface SourcingRunRepository extends JpaRepository<SourcingRun, UUID> {

    Optional<SourcingRun> findByProjectId(UUID projectId);
}
