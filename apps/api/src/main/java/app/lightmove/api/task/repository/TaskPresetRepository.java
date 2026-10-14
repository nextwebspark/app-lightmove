package app.lightmove.api.task.repository;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.task.model.TaskPreset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** A workspace's next-action presets. Every finder takes the workspace id: a preset is tenant data. */
public interface TaskPresetRepository extends JpaRepository<TaskPreset, UUID> {

    List<TaskPreset> findByWorkspaceIdOrderByPositionAscLabelAsc(UUID workspaceId);

    Optional<TaskPreset> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    boolean existsByWorkspaceId(UUID workspaceId);

    @Query("select p from TaskPreset p where p.workspaceId = :workspaceId and lower(p.label) = lower(:label)")
    Optional<TaskPreset> findByLabel(UUID workspaceId, String label);

    @Query("select coalesce(max(p.position), 0) from TaskPreset p where p.workspaceId = :workspaceId")
    int lastPositionOf(UUID workspaceId);

    default TaskPreset requireInWorkspace(UUID id, UUID workspaceId) {
        return findByIdAndWorkspaceId(id, workspaceId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    /** Seeds one starter preset; a second seeding racing this one, or an admin's own preset of that name, wins. */
    @Modifying
    @Query(value = """
            INSERT INTO app_lm_workspace_task_preset (workspace_id, label, kind, due_offset_days, position)
            VALUES (:workspaceId, :label, :kind, :dueOffsetDays, :position)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(UUID workspaceId, String label, String kind, Integer dueOffsetDays, int position);
}
