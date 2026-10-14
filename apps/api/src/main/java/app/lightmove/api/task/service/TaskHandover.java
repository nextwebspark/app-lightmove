package app.lightmove.api.task.service;

import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.core.security.rbac.ProjectAccess;
import app.lightmove.api.project.model.ProjectSeatVacated;
import app.lightmove.api.task.model.Task;
import app.lightmove.api.task.model.TasksMovedToLead;
import app.lightmove.api.task.repository.TaskRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A teammate leaving a position hands its lead their open tasks there, in the transaction that removes the seat,
 * so nothing they held is left with someone who can no longer open it. A position with no other lead — only a
 * delivered or closed one can be left so — keeps the tasks where they are.
 */
@Component
@RequiredArgsConstructor
class TaskHandover {

    private final TaskRepository tasks;
    private final ProjectAccess projectAccess;
    private final TaskTimeline timeline;
    private final ApplicationEventPublisher events;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onSeatVacated(ProjectSeatVacated vacated) {
        List<Task> held = tasks.findOpenOfAssigneeOnProject(vacated.projectId(), vacated.userId());
        if (held.isEmpty()) {
            return;
        }
        UUID lead = projectAccess.leadsOf(vacated.projectId()).stream()
                .filter(candidate -> !candidate.equals(vacated.userId()))
                .findFirst().orElse(null);
        if (lead == null) {
            return;
        }
        for (Task task : held) {
            task.reassign(lead);
            timeline.record(task, vacated.actorUserId(), PersonActivityKind.TASK_REASSIGNED, vacated.userId());
        }
        events.publishEvent(new TasksMovedToLead(vacated.workspaceId(), vacated.projectId(), lead, vacated.userId(),
                held.size()));
    }
}
