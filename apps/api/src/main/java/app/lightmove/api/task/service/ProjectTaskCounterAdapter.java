package app.lightmove.api.task.service;

import app.lightmove.api.project.service.ProjectTaskCounter;
import app.lightmove.api.task.constant.TaskStatus;
import app.lightmove.api.task.repository.TaskRepository;
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
