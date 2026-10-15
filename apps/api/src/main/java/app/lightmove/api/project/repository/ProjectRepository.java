package app.lightmove.api.project.repository;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.project.model.Project;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every finder carries the workspace id — an unscoped lookup on tenant data must not exist. */
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    /**
     * The archived positions, in JPQL and in SQL: the one definition every read that leaves them out — My tasks,
     * the due scan, the bell — is written against, as {@code project_id NOT IN (…)}.
     */
    String ARCHIVED_IDS_JPQL = "select archived.id from Project archived where archived.archivedAt is not null";
    String ARCHIVED_IDS_SQL =
            "SELECT archived.id FROM app_lm_project archived WHERE archived.archived_at IS NOT NULL";

    List<Project> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    List<Project> findByWorkspaceIdAndArchivedAtIsNullOrderByCreatedAtDesc(UUID workspaceId);

    boolean existsByIdAndArchivedAtIsNotNull(UUID id);

    /** Held to commit, so two archives of one position queue and the second changes nothing. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.id = :id and p.workspaceId = :workspaceId")
    Optional<Project> lockToArchive(UUID id, UUID workspaceId);

    /** Refuses whatever would start new work — outreach above all — on an archived position. */
    default void requireNotArchived(UUID id) {
        if (existsByIdAndArchivedAtIsNotNull(id)) {
            throw ApiException.of(ErrorCode.PROJECT_ARCHIVED);
        }
    }

    Optional<Project> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    /** Of {@code ids}, the ones that are this workspace's projects. */
    @Query("select p.id from Project p where p.workspaceId = :workspaceId and p.id in :ids")
    Set<UUID> findIdsInWorkspace(@Param("workspaceId") UUID workspaceId, @Param("ids") Collection<UUID> ids);

    /** The workspace's project, or a 404 — never a hint that the id exists in another tenant. */
    default Project requireInWorkspace(UUID id, UUID workspaceId) {
        return findByIdAndWorkspaceId(id, workspaceId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    List<Project> findByWorkspaceIdAndClientIdOrderByCreatedAtDesc(UUID workspaceId, UUID clientId);

    List<Project> findByWorkspaceIdAndClientIdAndArchivedAtIsNull(UUID workspaceId, UUID clientId);
}
