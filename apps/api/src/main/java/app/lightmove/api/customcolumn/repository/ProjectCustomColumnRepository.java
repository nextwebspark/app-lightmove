package app.lightmove.api.customcolumn.repository;

import app.lightmove.api.customcolumn.constant.CustomColumnTarget;
import app.lightmove.api.customcolumn.model.ProjectCustomColumn;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * A mandate's custom column definitions. Every finder carries the project id: a column set is mandate
 * content, and an unscoped lookup on it must not exist. The project itself is resolved against the
 * caller's workspace one layer up, the same way the triage and candidate repositories are used.
 */
public interface ProjectCustomColumnRepository extends JpaRepository<ProjectCustomColumn, UUID> {

    List<ProjectCustomColumn> findByProjectIdAndTargetOrderByDisplayOrderAscLabelAsc(
            UUID projectId, CustomColumnTarget target);

    /** Both grids' columns in one read, for the screen that shows companies and people together. */
    List<ProjectCustomColumn> findByProjectIdOrderByTargetAscDisplayOrderAscLabelAsc(UUID projectId);

    Optional<ProjectCustomColumn> findByIdAndProjectId(UUID id, UUID projectId);

    Optional<ProjectCustomColumn> findByProjectIdAndTargetAndFieldKey(
            UUID projectId, CustomColumnTarget target, String fieldKey);

    boolean existsByProjectIdAndTargetAndLabelIgnoreCase(
            UUID projectId, CustomColumnTarget target, String label);

    /** The per-project ceiling is counted across both grids — it is a limit on one screen's width. */
    long countByProjectId(UUID projectId);

    /** How many columns a reorder has to account for: one grid's whole set, and nothing beside it. */
    long countByProjectIdAndTarget(UUID projectId, CustomColumnTarget target);
}
