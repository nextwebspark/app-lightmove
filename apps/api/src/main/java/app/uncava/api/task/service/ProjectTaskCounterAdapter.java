package app.uncava.api.task.service;

import app.uncava.api.project.service.ProjectTaskCounter;
import app.uncava.api.task.constant.TaskStatus;
import app.uncava.api.task.repository.TaskRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Open tasks on one position, for the archive dialog's warning. */
@Component
@RequiredArgsConstructor
class ProjectTaskCounterAdapter implements ProjectTaskCounter {

    private final TaskRepository tasks;

    @Override
    public long countOpen(UUID projectId) {
        return tasks.countByProjectIdAndStatus(projectId, TaskStatus.OPEN);
    }
}
