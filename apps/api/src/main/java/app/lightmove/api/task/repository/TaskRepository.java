package app.lightmove.api.task.repository;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.task.constant.TaskStatus;
import app.lightmove.api.task.model.Task;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Tasks. Every finder takes the workspace or a project the caller already scoped: a task is tenant data. */
public interface TaskRepository extends JpaRepository<Task, UUID> {

    Optional<Task> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    default Task requireInWorkspace(UUID id, UUID workspaceId) {
        return findByIdAndWorkspaceId(id, workspaceId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    @Query("""
            select t from Task t where t.projectId = :projectId and t.candidateId in :candidateIds
              and t.status = :status
            """)
    List<Task> findOfCandidates(UUID projectId, Collection<UUID> candidateIds, TaskStatus status);

    @Query("""
            select t from Task t where t.projectId = :projectId and t.candidateId = :candidateId
              and t.status = app.lightmove.api.task.constant.TaskStatus.DONE
            order by t.completedAt desc
            """)
    List<Task> findDoneOfCandidate(UUID projectId, UUID candidateId, Limit limit);

    @Query("""
            select count(t) from Task t where t.projectId = :projectId and t.candidateId = :candidateId
              and t.status = app.lightmove.api.task.constant.TaskStatus.DONE
            """)
    long countDoneOfCandidate(UUID projectId, UUID candidateId);

    @Query("""
            select t from Task t where t.workspaceId = :workspaceId and t.assigneeUserId = :userId
              and t.status = app.lightmove.api.task.constant.TaskStatus.OPEN
            """)
    List<Task> findOpenAssignedTo(UUID workspaceId, UUID userId);

    /** Overdue before {@code now}, today until {@code tomorrow} (the viewer's next midnight), upcoming after or undated. */
    @Query(value = """
            SELECT count(*) FILTER (WHERE due_at < :now) AS overdue,
                   count(*) FILTER (WHERE due_at >= :now AND due_at < :tomorrow) AS today,
                   count(*) FILTER (WHERE due_at IS NULL OR due_at >= :tomorrow) AS upcoming
            FROM app_lm_task
            WHERE workspace_id = :workspaceId AND assignee_user_id = :userId AND status = 'OPEN'
            """, nativeQuery = true)
    MyTaskTally tallyOpenAssignedTo(UUID workspaceId, UUID userId, Instant now, Instant tomorrow);

    @Query("""
            select t from Task t where t.workspaceId = :workspaceId and t.assigneeUserId = :userId
              and t.status = app.lightmove.api.task.constant.TaskStatus.OPEN and t.dueAt < :until
            order by t.dueAt, t.createdAt, t.id
            limit :limit offset :offset
            """)
    List<Task> findOpenAssignedToDueBefore(UUID workspaceId, UUID userId, Instant until, long offset, int limit);

    @Query("""
            select t from Task t where t.workspaceId = :workspaceId and t.assigneeUserId = :userId
              and t.status = app.lightmove.api.task.constant.TaskStatus.OPEN and t.dueAt >= :from and t.dueAt < :until
            order by t.dueAt, t.createdAt, t.id
            limit :limit offset :offset
            """)
    List<Task> findOpenAssignedToDueBetween(UUID workspaceId, UUID userId, Instant from, Instant until,
                                            long offset, int limit);

    @Query("""
            select t from Task t where t.workspaceId = :workspaceId and t.assigneeUserId = :userId
              and t.status = app.lightmove.api.task.constant.TaskStatus.OPEN and (t.dueAt is null or t.dueAt >= :from)
            order by t.dueAt asc nulls last, t.createdAt, t.id
            limit :limit offset :offset
            """)
    List<Task> findOpenAssignedToDueFrom(UUID workspaceId, UUID userId, Instant from, long offset, int limit);

    /** Open tasks the caller put on somebody else. */
    @Query("""
            select t from Task t where t.workspaceId = :workspaceId and t.createdBy = :userId
              and t.assigneeUserId <> :userId and t.status = app.lightmove.api.task.constant.TaskStatus.OPEN
            order by t.dueAt asc nulls last, t.createdAt, t.id
            limit :limit offset :offset
            """)
    List<Task> findOpenAssignedBy(UUID workspaceId, UUID userId, long offset, int limit);

    @Query("""
            select count(t) from Task t where t.workspaceId = :workspaceId and t.createdBy = :userId
              and t.assigneeUserId <> :userId and t.status = app.lightmove.api.task.constant.TaskStatus.OPEN
            """)
    long countOpenAssignedBy(UUID workspaceId, UUID userId);

    @Query("""
            select t from Task t where t.projectId = :projectId and t.assigneeUserId = :userId
              and t.status = app.lightmove.api.task.constant.TaskStatus.OPEN
            """)
    List<Task> findOpenOfAssigneeOnProject(UUID projectId, UUID userId);

    @Query("""
            select distinct t.workspaceId as workspaceId, t.assigneeUserId as userId from Task t
            where t.status = app.lightmove.api.task.constant.TaskStatus.OPEN and t.dueAt <= :until
            """)
    List<TaskHolderRow> findHoldersDueBy(Instant until);

    boolean existsByWorkspaceIdAndRuleKey(UUID workspaceId, String ruleKey);

    @Query("""
            select t.presetId as presetId, count(t) as uses from Task t
            where t.workspaceId = :workspaceId and t.presetId is not null group by t.presetId
            """)
    List<TaskPresetUsage> usageOfPresets(UUID workspaceId);

    /** Titles typed rather than picked, three times or more since {@code since}, most typed first. */
    @Query(value = """
            SELECT min(t.title) AS label, count(*) AS uses
            FROM app_lm_task t
            WHERE t.workspace_id = :workspaceId AND t.preset_id IS NULL AND t.source <> 'RULE'
              AND t.created_at >= :since
              AND NOT EXISTS (SELECT 1 FROM app_lm_workspace_task_preset p
                              WHERE p.workspace_id = t.workspace_id AND lower(p.label) = lower(t.title))
            GROUP BY lower(t.title)
            HAVING count(*) >= 3
            ORDER BY count(*) DESC, lower(t.title)
            LIMIT 5
            """, nativeQuery = true)
    List<TypedTaskTitle> typedTitles(UUID workspaceId, Instant since);
}
