package app.uncava.api.task.service;

import app.uncava.api.candidate.constant.PersonActivityKind;
import app.uncava.api.core.security.rbac.ProjectAccess;
import app.uncava.api.core.security.rbac.ProjectAction;
import app.uncava.api.core.security.rbac.WorkspaceAccess;
import app.uncava.api.project.model.ProjectSeatVacated;
import app.uncava.api.task.model.Task;
import app.uncava.api.task.model.TasksMovedToLead;
import app.uncava.api.task.repository.TaskRepository;
import app.uncava.api.workspace.model.MemberAccessNarrowed;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A teammate leaving a position hands its lead their open tasks there, in the transaction that removes the seat,
 * so nothing they held is left with someone who can no longer open it. The same goes for a member who loses the
 * admin role or leaves the workspace: a task on a position they worked only as an admin goes to its lead. A position
 * with no other lead — only a delivered, closed or archived one can be left so — keeps the tasks where they are.
 */
@Component
@RequiredArgsConstructor
class TaskHandover {

    private final TaskRepository tasks;
    private final ProjectAccess projectAccess;
    private final WorkspaceAccess workspaceAccess;
    private final TaskTimeline timeline;
    private final ApplicationEventPublisher events;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onSeatVacated(ProjectSeatVacated vacated) {
        handOver(vacated.workspaceId(), vacated.projectId(), vacated.userId(), vacated.actorUserId(),
                tasks.findOpenOfAssigneeOnProject(vacated.projectId(), vacated.userId()));
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onAccessNarrowed(MemberAccessNarrowed narrowed) {
        Map<UUID, List<Task>> held = tasks.findOpenAssignedTo(narrowed.workspaceId(), narrowed.userId()).stream()
                .filter(task -> task.getProjectId() != null)
                .collect(Collectors.groupingBy(Task::getProjectId));
        if (held.isEmpty()) {
            return;
        }
        Set<UUID> kept = workspaceAccess.isStaff(narrowed.userId(), narrowed.workspaceId())
                ? projectAccess.projectsWithAction(narrowed.userId(), narrowed.workspaceId(), held.keySet(),
                        ProjectAction.WORK_EXECUTE)
                : Set.of();
        held.forEach((projectId, onProject) -> {
            if (!kept.contains(projectId)) {
                handOver(narrowed.workspaceId(), projectId, narrowed.userId(), narrowed.actorUserId(), onProject);
            }
        });
    }

    private void handOver(UUID workspaceId, UUID projectId, UUID leaving, UUID actor, List<Task> held) {
        if (held.isEmpty()) {
            return;
        }
        UUID lead = projectAccess.leadsOf(projectId).stream()
                .filter(candidate -> !candidate.equals(leaving))
                .findFirst().orElse(null);
        if (lead == null) {
            return;
        }
        for (Task task : held) {
            task.reassign(lead);
            timeline.record(task, actor, PersonActivityKind.TASK_REASSIGNED, leaving);
        }
        events.publishEvent(new TasksMovedToLead(workspaceId, projectId, lead, leaving, held.size()));
    }
}
