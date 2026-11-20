package app.lightmove.api.task.service;

import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.model.CandidateUnmapped;
import app.lightmove.api.task.constant.TaskStatus;
import app.lightmove.api.task.model.Task;
import app.lightmove.api.task.repository.TaskRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * An executive taken off a position takes their open tasks there with them; left open, they kept falling due. Not
 * audited: the async audit writer would outlive a removal that rolls back, and the removal is audited itself.
 */
@Component
@RequiredArgsConstructor
class UnmappedCandidateTasks {

    private final TaskRepository tasks;
    private final TaskTimeline timeline;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onUnmapped(CandidateUnmapped unmapped) {
        List<Task> open = tasks.findOfCandidates(unmapped.projectId(), List.of(unmapped.candidateId()),
                TaskStatus.OPEN);
        for (Task task : open) {
            task.cancel();
            task.releaseRuleKey();
            timeline.record(task, unmapped.removedBy(), PersonActivityKind.TASK_CANCELLED, null);
        }
    }
}
