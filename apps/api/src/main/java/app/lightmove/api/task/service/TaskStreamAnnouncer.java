package app.lightmove.api.task.service;

import app.lightmove.api.core.stream.ProjectStreamKind;
import app.lightmove.api.core.stream.ProjectStreamPublisher;
import app.lightmove.api.task.model.Task;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Tells the position's open screens that a task was filed, changed or closed, on every write of the row — as
 * {@code OutreachStreamAnnouncer} does for outreach, and inside the same transaction.
 */
@Component
@RequiredArgsConstructor
public class TaskStreamAnnouncer {

    // A provider, not the bean: Hibernate asks for this listener while the entity manager is still being built.
    private final ObjectProvider<ProjectStreamPublisher> stream;

    @PostPersist
    @PostUpdate
    @PostRemove
    void announce(Task changed) {
        if (changed.getProjectId() != null) {
            stream.getObject().publish(changed.getProjectId(), ProjectStreamKind.TASK);
        }
    }
}
